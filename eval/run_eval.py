#!/usr/bin/env python3
"""Run repository-grounded RAG and synthetic profiling evaluations."""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load_local_env():
    env_file = ROOT / ".env"
    if not env_file.is_file():
        return
    for raw in env_file.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip("\"'"))


load_local_env()
API = os.getenv("KPF_API_URL", "http://localhost:8080").rstrip("/")
OLLAMA = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434").rstrip("/")
MODEL = os.getenv("EVAL_JUDGE_MODEL", os.getenv("OLLAMA_MODEL", "llama3.2"))
TIMEOUT = int(os.getenv("EVAL_TIMEOUT", "120"))


def request(url, method="GET", data=None, token=None, accept=None):
    headers = {"Accept": accept or "application/json"}
    body = None
    if data is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(data, ensure_ascii=False).encode()
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=TIMEOUT) as response:
        return response.read().decode("utf-8")


def api_json(path, method="GET", data=None, token=None):
    return json.loads(request(API + path, method, data, token))


def login():
    user = os.getenv("KPF_EVAL_ADMIN_USERNAME", "demo-admin")
    password = os.getenv("KPF_EVAL_ADMIN_PASSWORD") or os.getenv("KPF_DEMO_ADMIN_PASSWORD")
    if not password:
        raise RuntimeError("Set KPF_EVAL_ADMIN_PASSWORD (or KPF_DEMO_ADMIN_PASSWORD) to run eval.")
    result = api_json("/api/auth/login", "POST", {"username": user, "password": password})
    return result.get("accessToken") or result.get("token")


def judge(question, expected, answer):
    prompt = ("You are a strict factual evaluator. Compare the answer only to the expected answer. "
              "Return JSON exactly {\"correct\":true} or {\"correct\":false}. Do not reward extra unsupported claims.\n"
              + json.dumps({"question": question, "expected": expected, "answer": answer}, ensure_ascii=False))
    body = json.dumps({"model": MODEL, "prompt": prompt, "stream": False, "format": "json",
                       "options": {"temperature": 0}}).encode()
    req = urllib.request.Request(OLLAMA + "/api/generate", data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as response:
        outer = json.loads(response.read().decode())
    return bool(json.loads(outer["response"]).get("correct")), "ollama"


def category(score):
    return "Prudent" if score <= 34 else "Équilibré" if score <= 69 else "Dynamique"


def expected_score(a):
    return ({"BEGINNER": 0, "INTERMEDIATE": 8, "EXPERIENCED": 15}[a["experience"]]
            + {"BASIC": 0, "MODERATE": 8, "ADVANCED": 15}[a["knowledge"]]
            + {"PRESERVATION": 0, "INCOME": 4, "GROWTH": 10}[a["objective"]]
            + (a["horizonYears"] * 20 + 39) // 40
            + {"LIMITED": 0, "MODERATE": 10, "COMFORTABLE": 20}[a["financialCapacity"]]
            + (a["riskTolerance"] - 1) * 5)


def stream_json(raw):
    for line in raw.splitlines():
        if line.startswith("data:"):
            try:
                return json.loads(line[5:].strip())
            except json.JSONDecodeError:
                continue
    raise RuntimeError("No JSON event returned by profiling SSE endpoint")


def main():
    questions = json.loads((ROOT / "eval/questions.json").read_text(encoding="utf-8"))
    personas = json.loads((ROOT / "eval/profiling-personas.json").read_text(encoding="utf-8"))
    token = login()
    # Markdown demo corpus is reindexed before each measured run; unchanged content is incremental.
    api_json("/api/trace/reindex", "POST", {"connectors": ["markdown"]}, token)

    rag_rows = []
    for item in questions:
        started = time.perf_counter()
        response = api_json("/api/rag/query", "POST", {"question": item["question"], "topK": 5}, token)
        elapsed = time.perf_counter() - started
        answer = response.get("answer", "")
        citations = response.get("sources", []) or []
        paths = [str(source.get("path", "")) for source in citations]
        source_ok = (not item["expected_sources"] and item["kind"] == "not_found" and not citations
                     and str(response.get("confidence", "")).upper() == "LOW") or any(
                         any(expected in path for expected in item["expected_sources"]) for path in paths)
        try:
            correct, judge_kind = judge(item["question"], item["expected_answer"], answer)
        except (urllib.error.URLError, TimeoutError, KeyError, ValueError, json.JSONDecodeError):
            # Deterministic offline fallback, explicitly marked in the report.
            normalized = answer.casefold()
            correct = all(word.casefold() in normalized for word in item["expected_keywords"])
            if item["kind"] == "not_found":
                correct = "information non trouvée" in normalized or "information non trouvee" in normalized
            judge_kind = "keywords-fallback"
        rag_rows.append({"id": item["id"], "kind": item["kind"], "correct": correct,
                         "citation": source_ok, "seconds": elapsed, "confidence": response.get("confidence", ""),
                         "paths": paths, "answer": answer, "judge": judge_kind})

    profile_rows = []
    for persona in personas:
        created = api_json("/api/profiling/sessions", "POST", token=token)
        session_id = created.get("sessionId")
        if not session_id:
            raise RuntimeError("Profiling session response has no sessionId")
        try:
            payload = request(f"{API}/api/profiling/sessions/{session_id}/messages", "POST",
                              {"message": persona["utterance"]}, token, "text/event-stream")
            turn = stream_json(payload)
            report = turn.get("profile")
            if not report and turn.get("complete"):
                report = api_json(f"/api/profiling/sessions/{session_id}/profile", token=token)
            actual = report.get("answers", {}) if report else {}
            expected = persona["expected"]
            extracted = sum(actual.get(k) == v for k, v in expected.items())
            score = expected_score(expected)
            profile_rows.append({"id": persona["id"], "extracted": extracted, "fields": len(expected),
                                 "score_consistent": bool(report and report.get("score") == score
                                                           and report.get("category") == category(score)),
                                 "expected_score": score, "actual_score": report.get("score") if report else None,
                                 "category": report.get("category") if report else "incomplete"})
        finally:
            try:
                request(f"{API}/api/profiling/sessions/{session_id}", "DELETE", token=token)
            except urllib.error.HTTPError:
                pass

    n = len(rag_rows)
    not_found = [r for r in rag_rows if r["kind"] == "not_found"]
    all_judges = sorted(set(r["judge"] for r in rag_rows))
    fields_total = sum(r["fields"] for r in profile_rows)
    fields_ok = sum(r["extracted"] for r in profile_rows)
    correct_rate = sum(r["correct"] for r in rag_rows) / n if n else 0
    citation_rate = sum(r["citation"] for r in rag_rows) / n if n else 0
    justified = sum(r["correct"] and r["citation"] and "LOW" in r["confidence"].upper() for r in not_found)
    report_lines = ["# Rapport d'évaluation", "", f"Exécuté le {datetime.now(timezone.utc).isoformat()}",
                    f"Juge : {', '.join(all_judges)} (modèle `{MODEL}`)", "", "## Benchmark RAG", "",
                    f"- Réponses correctes : {sum(r['correct'] for r in rag_rows)}/{n} ({correct_rate:.1%})",
                    f"- Citations attendues présentes : {sum(r['citation'] for r in rag_rows)}/{n} ({citation_rate:.1%})",
                    f"- Abstentions justifiées : {justified}/{len(not_found)} ({justified / len(not_found) if not_found else 0:.1%})",
                    f"- Temps moyen : {sum(r['seconds'] for r in rag_rows) / n if n else 0:.2f} s", "",
                    "| ID | Correct | Source/abstention | Confiance | Temps | Références |", "|---|---:|---:|---|---:|---|"]
    for r in rag_rows:
        report_lines.append(f"| {r['id']} | {'oui' if r['correct'] else 'non'} | {'oui' if r['citation'] else 'non'} | {r['confidence']} | {r['seconds']:.2f}s | {', '.join(r['paths']) or 'aucune'} |")
    report_lines += ["", "## Évaluation du profilage", "",
                     f"- Cohérence score/catégorie déterministe : {sum(r['score_consistent'] for r in profile_rows)}/{len(profile_rows)} ({sum(r['score_consistent'] for r in profile_rows) / len(profile_rows) if profile_rows else 0:.1%})",
                     f"- Exactitude d'extraction : {fields_ok}/{fields_total} champs ({fields_ok / fields_total if fields_total else 0:.1%})", "",
                     "| Persona | Champs extraits | Score attendu | Score retourné | Catégorie | Cohérent |", "|---|---:|---:|---:|---|---:|"]
    for r in profile_rows:
        report_lines.append(f"| {r['id']} | {r['extracted']}/{r['fields']} | {r['expected_score']} | {r['actual_score']} | {r['category']} | {'oui' if r['score_consistent'] else 'non'} |")
    report_lines += ["", "Les personas sont synthétiques. Les questions sans réponse attendue vérifient l'abstention et l'absence de citation. Un juge lexical de secours est indiqué si Ollama est indisponible ; il faut alors relancer avec le modèle local actif pour une mesure LLM.", ""]
    (ROOT / "eval/report.md").write_text("\n".join(report_lines), encoding="utf-8")
    print(f"Rapport écrit dans eval/report.md ; RAG correct {correct_rate:.1%}, citations {citation_rate:.1%}, extraction {fields_ok / fields_total if fields_total else 0:.1%}")


if __name__ == "__main__":
    try:
        main()
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, RuntimeError, ValueError) as error:
        print(f"Évaluation impossible : {error}", file=sys.stderr)
        sys.exit(1)

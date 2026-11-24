# Grille de score du profilage

La grille complète est également maintenue dans `docs/profiling/risk-scoring.md`. Expérience : débutant/intermédiaire/expérimenté = 0/8/15 points (max 15). Connaissances : basiques/modérées/avancées = 0/8/15 (max 15). Objectif préservation/revenus/croissance = 0/4/10 (max 10). Horizon de placement entre 1 et 40 ans = `ceil(années × 20 / 40)` (max 20). Capacité limitée/modérée/confortable = 0/10/20 (max 20). Tolérance au risque de 1 à 5 = `(niveau − 1) × 5` (max 20).

Le score additionne ces points. Catégories : Prudent 0–34, Équilibré 35–69, Dynamique 70–100. Un score maximal de 100 correspond à une expérience expérimentée, des connaissances avancées, un objectif de croissance, 40 ans, une capacité confortable et une tolérance de 5. Les règles sont déterministes et le LLM ne calcule pas le score.

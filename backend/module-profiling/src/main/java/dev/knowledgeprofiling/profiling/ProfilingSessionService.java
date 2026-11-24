package dev.knowledgeprofiling.profiling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProfilingSessionService {
    private static final String WARNING = "Prototype pédagogique fondé sur des réponses synthétiques ; ce résultat n'est ni un conseil financier ni une validation de conformité MiFID II.";
    private static final Map<String, String> QUESTIONS = Map.of(
            "experience", "Quelle expérience avez-vous des investissements : débutant, intermédiaire ou expérimenté ?",
            "knowledge", "Comment évaluez-vous vos connaissances des produits d'investissement : basiques, modérées ou avancées ?",
            "objective", "Quel est votre objectif principal : préserver le capital, obtenir des revenus ou faire croître le capital ?",
            "horizonYears", "Sur combien d'années envisagez-vous cet investissement (entre 1 et 40 ans) ?",
            "financialCapacity", "Sans communiquer de montant, quelle catégorie décrit votre capacité à absorber une perte : limitée, modérée ou confortable ?",
            "riskTolerance", "Sur une échelle de 1 à 5, quelle baisse temporaire pourriez-vous tolérer sans modifier votre objectif ?");

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final InterviewModel model;
    private final StructuredAnswerExtractor extractor = new StructuredAnswerExtractor();
    private final RiskScoringService scoring;
    private final RegulationKnowledgeBase regulations;
    private final byte[] pseudonymKey;

    public ProfilingSessionService(InterviewModel model, RiskScoringService scoring, RegulationKnowledgeBase regulations,
                                   @Value("${KPF_PSEUDONYM_SECRET:}") String pseudonymSecret) {
        this.model = model; this.scoring = scoring; this.regulations = regulations;
        if (pseudonymSecret == null || pseudonymSecret.isBlank()) {
            this.pseudonymKey = new byte[32];
            new java.security.SecureRandom().nextBytes(this.pseudonymKey);
        } else this.pseudonymKey = pseudonymSecret.getBytes(StandardCharsets.UTF_8);
    }

    public InterviewTurn create(String username) {
        UUID id = UUID.randomUUID();
        Session session = new Session(pseudonym(username));
        sessions.put(id, session);
        return turn(id, session, firstMissing(session.answers), QUESTIONS.get("experience"), "experience");
    }

    public InterviewTurn send(String username, UUID id, String message) {
        validateMessage(message);
        Session session = owned(username, id);
        synchronized (session) {
            ensurePresent(id, session);
            if (session.profile != null) throw new IllegalStateException("This interview is already complete");
            String history = String.join("\n", session.history);
            RegulationKnowledgeBase.Citation regulationContext = regulations.find("connaissances expérience situation financière capacité pertes objectifs tolérance risque");
            String extractedJson = model.extractAsJson(history, message, regulationContext);
            ExtractedAnswers extracted;
            try { extracted = extractor.extract(extractedJson); }
            catch (IllegalArgumentException e) { throw new InvalidExtractionException(); }
            session.answers = extracted.merge(session.answers);
            session.history.add("Utilisateur: " + message);
            String missing = firstMissing(session.answers);
            if (missing == null) {
                session.profile = buildProfile(id, session.answers);
                session.history.add("Entretien terminé.");
                return turn(id, session, null, "Merci. Le profil pédagogique est prêt.", null);
            }
            String question = safeFollowUp(extracted.nextQuestion(), missing);
            session.history.add("Assistant: " + question);
            return turn(id, session, missing, question, missing);
        }
    }

    public ProfileReport profile(String username, UUID id) {
        Session session = owned(username, id);
        synchronized (session) {
            ensurePresent(id, session);
            if (session.profile == null) throw new IllegalStateException("The interview is incomplete");
            return session.profile;
        }
    }

    public void delete(String username, UUID id) {
        Session session = owned(username, id);
        synchronized (session) {
            if (!sessions.remove(id, session)) throw new SessionNotFoundException();
            session.history.clear(); session.answers = null; session.profile = null;
        }
    }

    public Dashboard dashboard() {
        int prudent = 0, balanced = 0, dynamic = 0, complete = 0;
        for (Session session : sessions.values()) synchronized (session) {
            if (session.profile == null) continue;
            complete++;
            switch (session.profile.category()) {
                case "Prudent" -> prudent++;
                case "Équilibré" -> balanced++;
                case "Dynamique" -> dynamic++;
                default -> { }
            }
        }
        return new Dashboard(sessions.size(), complete, prudent, balanced, dynamic);
    }

    private ProfileReport buildProfile(UUID id, ProfilingAnswers answers) {
        ProfilingScore score = scoring.score(answers);
        String justification = score.breakdown().stream().map(item -> item.criterion() + " : " + item.points() + "/" + item.maximum())
                .reduce((a, b) -> a + "; " + b).orElse("");
        List<RegulationKnowledgeBase.Citation> citations = QUESTIONS.keySet().stream()
                .map(key -> regulations.find(key + " adéquation connaissance expérience situation financière objectif tolérance risque")).distinct().toList();
        return new ProfileReport(answers, score.score(), score.category(), justification, score.breakdown(),
                "Score pédagogique limité aux réponses déclarées ; aucune vérification de cohérence, de capacité réelle à subir des pertes, de besoins de liquidité ou de préférences de durabilité.", WARNING, citations);
    }
    private InterviewTurn turn(UUID id, Session session, String missing, String question, String questionKey) {
        RegulationKnowledgeBase.Citation citation = regulations.find(questionKey == null ? "adéquation connaissance expérience situation financière objectifs tolérance risque" : questionKey);
        String why = questionKey == null ? "Les réponses complètes permettent de résumer les facteurs pédagogiques du profil."
                : "Cette question porte sur " + criterionLabel(questionKey) + ", un facteur mentionné dans l'évaluation de l'adéquation.";
        return new InterviewTurn(id, question, missing, why, session.profile != null, citation, session.profile);
    }
    private String criterionLabel(String key) {
        return switch (key) {
            case "experience" -> "l'expérience en investissement";
            case "knowledge" -> "les connaissances en investissement";
            case "objective" -> "les objectifs d'investissement";
            case "horizonYears" -> "l'horizon de placement";
            case "financialCapacity" -> "la situation financière et la capacité à subir des pertes";
            case "riskTolerance" -> "la tolérance au risque";
            default -> "les facteurs de profilage";
        };
    }
    private String firstMissing(ProfilingAnswers answers) {
        if (answers == null || answers.experience() == null) return "experience";
        if (answers.knowledge() == null) return "knowledge";
        if (answers.objective() == null) return "objective";
        if (answers.horizonYears() == null) return "horizonYears";
        if (answers.financialCapacity() == null) return "financialCapacity";
        if (answers.riskTolerance() == null) return "riskTolerance";
        return null;
    }
    private String safeFollowUp(String proposal, String missing) {
        if (proposal == null || proposal.isBlank()) return QUESTIONS.get(missing);
        String normalized = proposal.toLowerCase(java.util.Locale.ROOT);
        if (List.of("nom", "adresse", "téléphone", "telephone", "email", "e-mail", "compte", "iban", "revenu exact", "salaire exact")
                .stream().anyMatch(normalized::contains)) return QUESTIONS.get(missing);
        return proposal;
    }
    private Session owned(String username, UUID id) {
        Session session = sessions.get(id);
        if (session == null || !MessageDigest.isEqual(session.owner.getBytes(StandardCharsets.US_ASCII), pseudonym(username).getBytes(StandardCharsets.US_ASCII)))
            throw new SessionNotFoundException();
        return session;
    }
    private void ensurePresent(UUID id, Session session) {
        if (sessions.get(id) != session) throw new SessionNotFoundException();
    }
    private String pseudonym(String username) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(pseudonymKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Could not pseudonymize session owner", e); }
    }
    private void validateMessage(String message) {
        if (message == null || message.isBlank() || message.length() > 2000) throw new IllegalArgumentException("Message must contain 1 to 2000 characters");
    }

    private static final class Session {
        private final String owner;
        private final List<String> history = new ArrayList<>();
        private ProfilingAnswers answers;
        private ProfileReport profile;
        private Session(String owner) { this.owner = owner; }
    }
    public record InterviewTurn(UUID sessionId, String assistantMessage, String missingCriterion, String whyQuestionAsked, boolean complete,
                                RegulationKnowledgeBase.Citation rationale, ProfileReport profile) { }
    public record ProfileReport(ProfilingAnswers answers, int score, String category, String justification,
                                List<ProfilingScore.CriterionScore> breakdown, String limitations, String warning,
                                List<RegulationKnowledgeBase.Citation> citations) {
        public ProfileReport { breakdown = List.copyOf(breakdown); citations = List.copyOf(citations); }
    }
    public record Dashboard(int activeSessions, int completedProfiles, int prudent, int balanced, int dynamic) { }
    public static final class SessionNotFoundException extends RuntimeException { }
    public static final class InvalidExtractionException extends RuntimeException { }
}

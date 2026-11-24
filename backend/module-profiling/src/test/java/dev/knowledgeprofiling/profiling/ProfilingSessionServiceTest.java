package dev.knowledgeprofiling.profiling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfilingSessionServiceTest {
    private final String completeSyntheticExtraction = "{\"experience\":\"INTERMEDIATE\",\"knowledge\":\"MODERATE\",\"objective\":\"GROWTH\",\"horizonYears\":12,\"financialCapacity\":\"MODERATE\",\"riskTolerance\":4,\"uncertainFields\":[],\"nextQuestion\":null}";

    @Test void packagedRegulatoryRagReturnsPublicCitedEvidence() {
        var citation = new RegulationKnowledgeBase("missing-regulation").find("connaissances expérience situation financière tolérance risque");
        assertTrue(citation.url().startsWith("https://www."));
        assertTrue(citation.excerpt().contains("Résumé") || citation.excerpt().contains("Résumé pédagogique"));
    }

    @Test void syntheticConversationProducesRepeatableProfileAndCanBeDeleted() {
        InterviewModel fakeModel = (history, message, context) -> completeSyntheticExtraction;
        var sessions = service(fakeModel);
        var first = sessions.create("synthetic-user-a");
        assertEquals("experience", first.missingCriterion());
        var result = sessions.send("synthetic-user-a", first.sessionId(), "Réponses de démonstration complètes.");
        assertTrue(result.complete());
        assertEquals("Équilibré", result.profile().category());
        assertEquals(result.profile().score(), sessions.profile("synthetic-user-a", first.sessionId()).score());
        assertFalse(result.profile().citations().isEmpty());
        assertThrows(ProfilingSessionService.SessionNotFoundException.class,
                () -> sessions.profile("synthetic-user-b", first.sessionId()));
        assertThrows(ProfilingSessionService.SessionNotFoundException.class,
                () -> sessions.delete("synthetic-user-b", first.sessionId()));
        sessions.delete("synthetic-user-a", first.sessionId());
        assertEquals(0, sessions.dashboard().activeSessions());
    }

    @Test void missingAndAmbiguousModelFieldsKeepInterviewOpenWithFollowUp() {
        InterviewModel fakeModel = (history, message, context) -> "{\"experience\":null,\"knowledge\":null,\"objective\":null,\"horizonYears\":null,\"financialCapacity\":null,\"riskTolerance\":null,\"uncertainFields\":[\"experience\"],\"nextQuestion\":\"Pouvez-vous préciser votre expérience ?\"}";
        var sessions = service(fakeModel);
        var created = sessions.create("synthetic-user");
        var turn = sessions.send("synthetic-user", created.sessionId(), "Je ne sais pas.");
        assertFalse(turn.complete());
        assertEquals("experience", turn.missingCriterion());
        assertEquals("Pouvez-vous préciser votre expérience ?", turn.assistantMessage());
    }

    @Test void invalidModelJsonDoesNotCreateAProfile() {
        var sessions = service((history, message, context) -> "{not-json");
        var created = sessions.create("synthetic-user");
        assertThrows(ProfilingSessionService.InvalidExtractionException.class, () -> sessions.send("synthetic-user", created.sessionId(), "Réponse synthétique"));
        assertEquals(0, sessions.dashboard().completedProfiles());
    }

    @Test void completedProfileHasAReadablePdfExport() throws Exception {
        var sessions = service((history, message, context) -> completeSyntheticExtraction);
        var started = sessions.create("synthetic-user");
        sessions.send("synthetic-user", started.sessionId(), "Réponses synthétiques.");
        byte[] pdf = new ProfilePdfExporter().export(sessions.profile("synthetic-user", started.sessionId()));
        assertTrue(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF"));
    }

    private ProfilingSessionService service(InterviewModel model) {
        return new ProfilingSessionService(model, new RiskScoringService(), new RegulationKnowledgeBase("missing-regulation"), "synthetic-test-secret");
    }
}

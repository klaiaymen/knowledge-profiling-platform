package dev.knowledgeprofiling.profiling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiskScoringServiceTest {
    private final RiskScoringService service = new RiskScoringService();
    @Test void deterministicScoreUsesDocumentedInputs() {
        var answers = new InvestorAnswers(10, 3, 2);
        assertEquals(72, service.score(answers).riskScore());
        assertEquals(service.score(answers), service.score(answers));
    }
    @Test void rejectsOutOfRangeAnswers() { assertThrows(IllegalArgumentException.class, () -> service.score(new InvestorAnswers(0, 3, 2))); }
    @Test void classifiesLowAndHighScores() {
        assertEquals("conservative", service.score(new InvestorAnswers(1, 1, 0)).riskLabel());
        assertEquals("dynamic", service.score(new InvestorAnswers(40, 5, 3)).riskLabel());
    }

    @Test void structuredGridHasStableFrenchCategoriesAtBoundariesAndLimits() {
        var prudent = new ProfilingAnswers(ProfilingAnswers.Experience.BEGINNER, ProfilingAnswers.Knowledge.BASIC,
                ProfilingAnswers.Objective.PRESERVATION, 1, ProfilingAnswers.FinancialCapacity.LIMITED, 1);
        assertEquals(1, service.score(prudent).score());
        assertEquals("Prudent", service.score(prudent).category());

        var balancedBoundary = new ProfilingAnswers(ProfilingAnswers.Experience.BEGINNER, ProfilingAnswers.Knowledge.MODERATE,
                ProfilingAnswers.Objective.GROWTH, 13, ProfilingAnswers.FinancialCapacity.LIMITED, 3);
        assertEquals(35, service.score(balancedBoundary).score());
        assertEquals("Équilibré", service.score(balancedBoundary).category());

        var dynamicBoundary = new ProfilingAnswers(ProfilingAnswers.Experience.EXPERIENCED, ProfilingAnswers.Knowledge.ADVANCED,
                ProfilingAnswers.Objective.GROWTH, 40, ProfilingAnswers.FinancialCapacity.MODERATE, 1);
        assertEquals(70, service.score(dynamicBoundary).score());
        assertEquals("Dynamique", service.score(dynamicBoundary).category());
        assertEquals(100, service.score(new ProfilingAnswers(ProfilingAnswers.Experience.EXPERIENCED,
                ProfilingAnswers.Knowledge.ADVANCED, ProfilingAnswers.Objective.GROWTH, 40,
                ProfilingAnswers.FinancialCapacity.COMFORTABLE, 5)).score());
    }

    @Test void structuredGridRejectsMissingAnswersAndOutOfRangeLimits() {
        assertThrows(IllegalArgumentException.class, () -> service.score(new ProfilingAnswers(null, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.score(new ProfilingAnswers(ProfilingAnswers.Experience.BEGINNER,
                ProfilingAnswers.Knowledge.BASIC, ProfilingAnswers.Objective.GROWTH, 41,
                ProfilingAnswers.FinancialCapacity.LIMITED, 1)));
    }
}

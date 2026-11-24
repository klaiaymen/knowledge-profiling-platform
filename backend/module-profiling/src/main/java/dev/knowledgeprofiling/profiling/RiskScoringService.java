package dev.knowledgeprofiling.profiling;

@org.springframework.stereotype.Service
public final class RiskScoringService {
    /** Deterministic educational grid, maximum 100 points; never delegated to an LLM. */
    public ProfilingScore score(ProfilingAnswers answers) {
        if (answers == null || !answers.isComplete()) throw new IllegalArgumentException("All profiling answers are required");
        if (answers.horizonYears() < 1 || answers.horizonYears() > 40) throw new IllegalArgumentException("horizonYears must be between 1 and 40");
        if (answers.riskTolerance() < 1 || answers.riskTolerance() > 5) throw new IllegalArgumentException("riskTolerance must be between 1 and 5");
        int experience = switch (answers.experience()) { case BEGINNER -> 0; case INTERMEDIATE -> 8; case EXPERIENCED -> 15; };
        int knowledge = switch (answers.knowledge()) { case BASIC -> 0; case MODERATE -> 8; case ADVANCED -> 15; };
        int objective = switch (answers.objective()) { case PRESERVATION -> 0; case INCOME -> 4; case GROWTH -> 10; };
        int horizon = (answers.horizonYears() * 20 + 39) / 40;
        int capacity = switch (answers.financialCapacity()) { case LIMITED -> 0; case MODERATE -> 10; case COMFORTABLE -> 20; };
        int tolerance = (answers.riskTolerance() - 1) * 5;
        int total = experience + knowledge + objective + horizon + capacity + tolerance;
        String category = total < 35 ? "Prudent" : total < 70 ? "Équilibré" : "Dynamique";
        return new ProfilingScore(total, category, java.util.List.of(
                new ProfilingScore.CriterionScore("Expérience", experience, 15, "Niveau déclaré"),
                new ProfilingScore.CriterionScore("Connaissances", knowledge, 15, "Niveau déclaré"),
                new ProfilingScore.CriterionScore("Objectif", objective, 10, "Préservation, revenus ou croissance"),
                new ProfilingScore.CriterionScore("Horizon", horizon, 20, "Horizon déclaré sur une échelle de 1 à 40 ans"),
                new ProfilingScore.CriterionScore("Capacité financière", capacity, 20, "Catégorie déclarative, sans montant exact"),
                new ProfilingScore.CriterionScore("Tolérance au risque", tolerance, 20, "Échelle déclarée de 1 à 5")));
    }

    /** Legacy demo endpoint retained for compatibility with Phase 1's sample UI. */
    public InvestorProfile score(InvestorAnswers answers) {
        if (answers == null) throw new IllegalArgumentException("Answers are required");
        checkRange("horizonYears", answers.horizonYears(), 1, 40);
        checkRange("lossTolerance", answers.lossTolerance(), 1, 5);
        checkRange("experience", answers.experience(), 0, 3);
        int score = Math.min(100, answers.horizonYears() * 2 + answers.lossTolerance() * 12 + answers.experience() * 8);
        String label = score < 40 ? "conservative" : score < 70 ? "balanced" : "dynamic";
        return new InvestorProfile(answers, score, label);
    }
    private void checkRange(String field, int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
    }
}

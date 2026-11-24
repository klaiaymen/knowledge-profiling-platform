package dev.knowledgeprofiling.profiling;

public record ProfilingAnswers(
        Experience experience,
        Knowledge knowledge,
        Objective objective,
        Integer horizonYears,
        FinancialCapacity financialCapacity,
        Integer riskTolerance) {

    public boolean isComplete() {
        return experience != null && knowledge != null && objective != null &&
                horizonYears != null && financialCapacity != null && riskTolerance != null;
    }

    public enum Experience { BEGINNER, INTERMEDIATE, EXPERIENCED }
    public enum Knowledge { BASIC, MODERATE, ADVANCED }
    public enum Objective { PRESERVATION, INCOME, GROWTH }
    public enum FinancialCapacity { LIMITED, MODERATE, COMFORTABLE }
}

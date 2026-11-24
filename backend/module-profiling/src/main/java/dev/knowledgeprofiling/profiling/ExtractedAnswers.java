package dev.knowledgeprofiling.profiling;

/** A model extraction may be incomplete; null means not stated or ambiguous. */
public record ExtractedAnswers(
        ProfilingAnswers.Experience experience,
        ProfilingAnswers.Knowledge knowledge,
        ProfilingAnswers.Objective objective,
        Integer horizonYears,
        ProfilingAnswers.FinancialCapacity financialCapacity,
        Integer riskTolerance,
        java.util.List<String> uncertainFields,
        String nextQuestion) {
    public ExtractedAnswers {
        uncertainFields = uncertainFields == null ? java.util.List.of() : java.util.List.copyOf(uncertainFields);
    }
    public ProfilingAnswers merge(ProfilingAnswers previous) {
        ProfilingAnswers base = previous == null ? new ProfilingAnswers(null, null, null, null, null, null) : previous;
        return new ProfilingAnswers(or("experience", experience, base.experience()), or("knowledge", knowledge, base.knowledge()),
                or("objective", objective, base.objective()), or("horizonYears", horizonYears, base.horizonYears()),
                or("financialCapacity", financialCapacity, base.financialCapacity()), or("riskTolerance", riskTolerance, base.riskTolerance()));
    }
    private <T> T or(String name, T value, T fallback) { return uncertainFields.contains(name) ? null : value == null ? fallback : value; }
}

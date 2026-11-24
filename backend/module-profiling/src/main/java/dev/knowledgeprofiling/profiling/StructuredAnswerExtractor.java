package dev.knowledgeprofiling.profiling;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class StructuredAnswerExtractor {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public ExtractedAnswers extract(String json) {
        try {
            ExtractedAnswers result = MAPPER.readValue(json, ExtractedAnswers.class);
            if (result.horizonYears() != null && (result.horizonYears() < 1 || result.horizonYears() > 40))
                throw new IllegalArgumentException("horizonYears must be between 1 and 40");
            if (result.riskTolerance() != null && (result.riskTolerance() < 1 || result.riskTolerance() > 5))
                throw new IllegalArgumentException("riskTolerance must be between 1 and 5");
            java.util.Set<String> allowed = java.util.Set.of("experience", "knowledge", "objective", "horizonYears", "financialCapacity", "riskTolerance");
            if (result.uncertainFields().stream().anyMatch(field -> !allowed.contains(field)))
                throw new IllegalArgumentException("uncertainFields contains an unknown criterion");
            if (result.nextQuestion() != null && result.nextQuestion().length() > 300)
                throw new IllegalArgumentException("nextQuestion is too long");
            return result;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("Invalid structured interview JSON", e);
        }
    }
}

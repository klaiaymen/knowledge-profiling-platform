package dev.knowledgeprofiling.profiling;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class AnswerJsonExtractor {
    private final ObjectMapper mapper = new ObjectMapper();
    public InvestorAnswers extract(String json) {
        try { return mapper.readValue(json, InvestorAnswers.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid structured answers", e); }
    }
}

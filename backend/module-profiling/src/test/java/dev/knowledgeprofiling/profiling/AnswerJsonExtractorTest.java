package dev.knowledgeprofiling.profiling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnswerJsonExtractorTest {
    @Test void extractsSyntheticStructuredAnswers() {
        assertEquals(new InvestorAnswers(8, 2, 1), new AnswerJsonExtractor().extract("{\"horizonYears\":8,\"lossTolerance\":2,\"experience\":1}"));
    }
    @Test void rejectsInvalidJson() { assertThrows(IllegalArgumentException.class, () -> new AnswerJsonExtractor().extract("not json")); }

    @Test void validatesStructuredExtractionSchemaAndAmbiguity() {
        var extracted = new StructuredAnswerExtractor().extract("{\"experience\":null,\"knowledge\":\"BASIC\",\"objective\":null,\"horizonYears\":null,\"financialCapacity\":null,\"riskTolerance\":null,\"uncertainFields\":[\"experience\"],\"nextQuestion\":\"Quel niveau ?\"}");
        assertEquals(java.util.List.of("experience"), extracted.uncertainFields());
        assertNull(extracted.merge(null).experience());
        assertThrows(IllegalArgumentException.class, () -> new StructuredAnswerExtractor().extract("{\"experience\":\"BEGINNER\",\"unexpected\":true}"));
        assertThrows(IllegalArgumentException.class, () -> new StructuredAnswerExtractor().extract("{\"horizonYears\":99}"));
    }
}

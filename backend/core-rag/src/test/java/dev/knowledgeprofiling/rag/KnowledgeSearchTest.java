package dev.knowledgeprofiling.rag;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeSearchTest {
    @Test void returnsMatchingDocumentWithSourceLocation() {
        var doc = new SourceDocument("rules", "Rules", "docs/rules.md", "Risk score uses horizon", "markdown");
        var results = new KnowledgeSearch().search("horizon", List.of(doc));
        assertEquals(List.of(doc), results);
        assertEquals("docs/rules.md", results.getFirst().location());
    }
    @Test void blankQueriesReturnNoResults() { assertTrue(new KnowledgeSearch().search(" ", List.of()).isEmpty()); }
}

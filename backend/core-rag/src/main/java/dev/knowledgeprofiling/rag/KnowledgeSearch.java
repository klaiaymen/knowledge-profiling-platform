package dev.knowledgeprofiling.rag;

import java.util.List;

/** Lexical search retained for the lightweight traceability demonstration. */
public final class KnowledgeSearch {
    public List<SourceDocument> search(String query, List<SourceDocument> documents) {
        if (query == null || query.isBlank()) return List.of();
        var terms = List.of(query.toLowerCase().split("\\s+"));
        return documents.stream().filter(d -> terms.stream().anyMatch(t -> d.content().toLowerCase().contains(t))).toList();
    }
}

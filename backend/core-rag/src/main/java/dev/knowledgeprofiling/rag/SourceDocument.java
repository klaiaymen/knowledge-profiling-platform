package dev.knowledgeprofiling.rag;

/** Legacy connector result kept for the traceability module. */
public record SourceDocument(String id, String title, String location, String content, String sourceType) { }

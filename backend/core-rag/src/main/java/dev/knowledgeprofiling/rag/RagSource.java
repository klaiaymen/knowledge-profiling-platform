package dev.knowledgeprofiling.rag;

public record RagSource(String citation, String excerpt, String sourceType, String sourceId,
                        String title, String path, Integer page, Integer line, String url) { }

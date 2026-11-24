package dev.knowledgeprofiling.rag;

import java.util.UUID;

public record SourceMetadata(
        String sourceType,
        UUID sourceId,
        String title,
        String path,
        Integer page,
        Integer line,
        String url) { }

package dev.knowledgeprofiling.traceability.connector;

import java.time.Instant;
import java.util.List;

public record TraceSourceDocument(String sourceType, String externalId, String repository, String title,
                                  String path, String url, String content, Instant updatedAt,
                                  List<String> relatedExternalIds, List<String> relatedPaths) {
    public TraceSourceDocument {
        relatedExternalIds = relatedExternalIds == null ? List.of() : List.copyOf(relatedExternalIds);
        relatedPaths = relatedPaths == null ? List.of() : List.copyOf(relatedPaths);
        content = content == null ? "" : content;
        title = title == null || title.isBlank() ? externalId : title;
    }
}

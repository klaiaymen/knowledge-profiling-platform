package dev.knowledgeprofiling.traceability.connector;

import java.util.List;

public interface SourceConnector {
    String name();
    List<TraceSourceDocument> fetch();
}

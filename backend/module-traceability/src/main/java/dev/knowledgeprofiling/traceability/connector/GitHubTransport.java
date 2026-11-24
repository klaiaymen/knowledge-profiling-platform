package dev.knowledgeprofiling.traceability.connector;

import java.io.IOException;

@FunctionalInterface
public interface GitHubTransport {
    String get(String url, String token) throws IOException, InterruptedException;
}

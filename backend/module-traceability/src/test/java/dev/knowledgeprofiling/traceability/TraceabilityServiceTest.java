package dev.knowledgeprofiling.traceability;

import dev.knowledgeprofiling.traceability.connector.GitHubConnector;
import dev.knowledgeprofiling.traceability.connector.MarkdownConnector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TraceabilityServiceTest {
    @TempDir Path temporaryDirectory;

    @Test void markdownConnectorReturnsStableRelativeSourceLocations() throws Exception {
        Path page = temporaryDirectory.resolve("architecture/rule.md");
        Files.createDirectories(page.getParent());
        Files.writeString(page, "Synthetic deterministic rule.");
        var source = new MarkdownConnector(temporaryDirectory).fetch().getFirst();
        assertEquals("architecture/rule.md", source.path());
        assertEquals("Synthetic deterministic rule.", source.content());
    }

    @Test void publicGithubRepositoryIsReadWithoutTokenAndIssueReferencesAreExtracted() {
        List<String> requestedUrls = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        var connector = new GitHubConnector("", "acme/demo", "https://api.github.test", (url, token) -> {
            requestedUrls.add(url); tokens.add(token);
            return switch (url.substring("https://api.github.test".length())) {
                case "/repos/acme/demo" -> "{\"default_branch\":\"main\"}";
                case "/repos/acme/demo/git/trees/main?recursive=1" -> "{\"tree\":[]}";
                case "/repos/acme/demo/issues?state=all&per_page=50" -> "[{\"number\":12,\"title\":\"Rule\",\"body\":\"Risk policy\",\"html_url\":\"https://github.com/acme/demo/issues/12\",\"updated_at\":\"2026-01-01T00:00:00Z\"}]";
                case "/repos/acme/demo/pulls?state=all&per_page=30" -> "[{\"number\":7,\"title\":\"Fixes #12\",\"body\":\"Adjust rule\",\"html_url\":\"https://github.com/acme/demo/pull/7\",\"updated_at\":\"2026-01-02T00:00:00Z\"}]";
                case "/repos/acme/demo/pulls/7/files?per_page=100" -> "[{\"filename\":\"src/Rule.java\"}]";
                case "/repos/acme/demo/pulls/7/commits?per_page=100" -> "[{\"sha\":\"abc123\",\"commit\":{\"message\":\"Fixes #12\"}}]";
                case "/repos/acme/demo/commits?per_page=30" -> "[]";
                default -> throw new IllegalArgumentException("Unexpected simulated GitHub request: " + url);
            };
        });
        var docs = connector.fetch();
        var pullRequest = docs.stream().filter(doc -> doc.sourceType().equals("github-pr")).findFirst().orElseThrow();
        assertTrue(pullRequest.relatedExternalIds().contains("acme/demo#issue:12"));
        assertTrue(pullRequest.relatedExternalIds().contains("acme/demo#commit:abc123"));
        assertTrue(pullRequest.relatedPaths().contains("src/Rule.java"));
        assertEquals(7, requestedUrls.size());
        assertTrue(tokens.stream().allMatch(String::isBlank));
    }
}

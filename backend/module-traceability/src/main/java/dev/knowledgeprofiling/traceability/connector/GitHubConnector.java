package dev.knowledgeprofiling.traceability.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
@Order(2)
public class GitHubConnector implements SourceConnector {
    private final String token;
    private final String repository;
    private final String apiUrl;
    private final GitHubTransport transport;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public GitHubConnector(@Value("${GITHUB_TOKEN:}") String token,
                           @Value("${KPF_GITHUB_REPOSITORY:}") String repository,
                           @Value("${app.traceability.github.api-url:https://api.github.com}") String apiUrl) {
        this.token = token;
        this.repository = repository;
        this.apiUrl = apiUrl.replaceAll("/$", "");
        HttpClient client = HttpClient.newHttpClient();
        this.transport = (url, accessToken) -> {
            var builder = HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/vnd.github+json").GET();
            if (!accessToken.isBlank()) builder.header("Authorization", "Bearer " + accessToken);
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("GitHub returned HTTP " + response.statusCode());
            return response.body();
        };
    }

    public GitHubConnector(String token, String repository, String apiUrl, GitHubTransport transport) {
        this.token = token;
        this.repository = repository;
        this.apiUrl = apiUrl.replaceAll("/$", "");
        this.transport = transport;
    }

    @Override public String name() { return "github"; }

    @Override public List<TraceSourceDocument> fetch() {
        if (repository.isBlank() || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) return List.of();
        try {
            String root = apiUrl + "/repos/" + repository;
            JsonNode repo = get(root);
            String branch = repo.path("default_branch").asText("main");
            JsonNode tree = get(root + "/git/trees/" + branch + "?recursive=1").path("tree");
            List<TraceSourceDocument> docs = new ArrayList<>();
            for (JsonNode entry : tree) {
                String path = entry.path("path").asText();
                if (!"blob".equals(entry.path("type").asText()) || !isTextFile(path) || docs.size() >= 50) continue;
                JsonNode file = get(root + "/contents/" + encodePath(path));
                String content = file.path("encoding").asText().equals("base64")
                        ? new String(Base64.getMimeDecoder().decode(file.path("content").asText()), StandardCharsets.UTF_8)
                        : file.path("content").asText();
                docs.add(new TraceSourceDocument("github-file", repository + "#file:" + path, repository,
                        path.substring(path.lastIndexOf('/') + 1), path,
                        "https://github.com/" + repository + "/blob/" + branch + "/" + path,
                        content, parseInstant(file.path("sha").asText()), List.of(), List.of()));
            }
            for (JsonNode issue : get(root + "/issues?state=all&per_page=50")) {
                if (issue.has("pull_request")) continue;
                int number = issue.path("number").asInt();
                docs.add(new TraceSourceDocument("github-issue", repository + "#issue:" + number, repository,
                        issue.path("title").asText("Issue #" + number), "#" + number,
                        issue.path("html_url").asText(), issue.path("title").asText("") + "\n" + issue.path("body").asText(""),
                        parseInstant(issue.path("updated_at").asText()), List.of(), List.of()));
            }
            for (JsonNode pr : get(root + "/pulls?state=all&per_page=30")) {
                int number = pr.path("number").asInt();
                List<String> paths = new ArrayList<>();
                for (JsonNode file : get(root + "/pulls/" + number + "/files?per_page=100")) paths.add(file.path("filename").asText());
                List<String> refs = references(pr.path("title").asText("") + "\n" + pr.path("body").asText(""));
                StringBuilder body = new StringBuilder(pr.path("title").asText("")).append('\n').append(pr.path("body").asText(""));
                paths.forEach(path -> body.append("\nChanged file: ").append(path));
                List<String> related = new ArrayList<>(refs);
                for (JsonNode commit : get(root + "/pulls/" + number + "/commits?per_page=100")) {
                    String sha = commit.path("sha").asText();
                    related.add(repository + "#commit:" + sha);
                    body.append("\nCommit ").append(sha).append(": ").append(commit.path("commit").path("message").asText(""));
                }
                docs.add(new TraceSourceDocument("github-pr", repository + "#pr:" + number, repository,
                        pr.path("title").asText("PR #" + number), "#" + number, pr.path("html_url").asText(),
                        body.toString(), parseInstant(pr.path("updated_at").asText()), related, paths));
            }
            for (JsonNode commit : get(root + "/commits?per_page=30")) {
                String sha = commit.path("sha").asText();
                List<String> paths = new ArrayList<>();
                for (JsonNode file : commit.path("files")) paths.add(file.path("filename").asText());
                String message = commit.path("commit").path("message").asText("");
                if (!paths.isEmpty()) message += "\nChanged files: " + String.join(", ", paths);
                docs.add(new TraceSourceDocument("github-commit", repository + "#commit:" + sha, repository,
                        message.lines().findFirst().orElse(sha), sha,
                        "https://github.com/" + repository + "/commit/" + sha,
                        message, parseInstant(commit.path("commit").path("committer").path("date").asText()),
                        references(message), paths));
            }
            return List.copyOf(docs);
        } catch (IOException e) { throw new IllegalStateException("GitHub source ingestion failed", e); }
    }

    private JsonNode get(String url) throws IOException {
        try { return mapper.readTree(transport.get(url, token)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("GitHub ingestion was interrupted", e); }
    }

    private boolean isTextFile(String path) {
        String lower = path.toLowerCase();
        return !lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg") &&
                !lower.endsWith(".gif") && !lower.endsWith(".pdf") && !lower.endsWith(".zip") &&
                !lower.endsWith(".jar") && !lower.endsWith(".class") && !lower.endsWith(".lock");
    }
    private String encodePath(String path) { return java.util.Arrays.stream(path.split("/")).map(s -> java.net.URLEncoder.encode(s, StandardCharsets.UTF_8)).reduce((a,b) -> a + "/" + b).orElse(""); }
    private List<String> references(String text) {
        var matcher = java.util.regex.Pattern.compile("(?i)(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\\s+#(\\d+)|#(\\d+)").matcher(text);
        List<String> refs = new ArrayList<>();
        while (matcher.find()) refs.add(repository + "#issue:" + (matcher.group(1) == null ? matcher.group(2) : matcher.group(1)));
        return refs.stream().distinct().toList();
    }
    private Instant parseInstant(String value) { try { return Instant.parse(value); } catch (Exception ignored) { return null; } }
}

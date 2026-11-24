package dev.knowledgeprofiling.traceability.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
@Order(3)
@ConditionalOnProperty(name = "app.traceability.jira.enabled", havingValue = "true")
public class JiraConnector implements SourceConnector {
    private final String baseUrl, email, token, projects;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public JiraConnector(@Value("${JIRA_BASE_URL:}") String baseUrl, @Value("${JIRA_EMAIL:}") String email,
                         @Value("${JIRA_API_TOKEN:}") String token, @Value("${JIRA_PROJECT_KEYS:}") String projects) {
        this.baseUrl = baseUrl.replaceAll("/$", ""); this.email = email; this.token = token; this.projects = projects;
    }
    @Override public String name() { return "jira"; }
    @Override public List<TraceSourceDocument> fetch() {
        if (baseUrl.isBlank() || email.isBlank() || token.isBlank() || projects.isBlank())
            throw new IllegalStateException("Jira is enabled but its environment settings are incomplete");
        try {
            String jql = "project in (" + projects.replaceAll("[^A-Za-z0-9_, -]", "") + ") ORDER BY updated DESC";
            String url = baseUrl + "/rest/api/3/search?jql=" + URLEncoder.encode(jql, StandardCharsets.UTF_8) + "&maxResults=100&fields=summary,description,updated,status,issuetype";
            JsonNode issues = get(url).path("issues");
            List<TraceSourceDocument> documents = new ArrayList<>();
            for (JsonNode issue : issues) {
                JsonNode fields = issue.path("fields");
                String key = issue.path("key").asText();
                String summary = fields.path("summary").asText(key);
                String status = fields.path("status").path("name").asText("");
                documents.add(new TraceSourceDocument("jira-issue", key, "", summary, key,
                        baseUrl + "/browse/" + key, summary + "\n" + status + "\n" + fields.path("description").toString(),
                        parseInstant(fields.path("updated").asText()), List.of(), List.of()));
            }
            return List.copyOf(documents);
        } catch (IOException e) { throw new IllegalStateException("Jira source ingestion failed", e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Jira ingestion was interrupted", e); }
    }

    private JsonNode get(String url) throws IOException, InterruptedException {
        String basic = Base64.getEncoder().encodeToString((email + ":" + token).getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Basic " + basic)
                .header("Accept", "application/json").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("Jira returned HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }
    private Instant parseInstant(String value) { try { return Instant.parse(value); } catch (Exception ignored) { return null; } }
}

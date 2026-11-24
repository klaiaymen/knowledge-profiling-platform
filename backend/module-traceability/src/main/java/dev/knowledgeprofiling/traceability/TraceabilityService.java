package dev.knowledgeprofiling.traceability;

import dev.knowledgeprofiling.rag.ChunkingService;
import dev.knowledgeprofiling.rag.ExtractedPage;
import dev.knowledgeprofiling.rag.KnowledgeSearch;
import dev.knowledgeprofiling.rag.RagAnswer;
import dev.knowledgeprofiling.rag.RagService;
import dev.knowledgeprofiling.rag.SourceDocument;
import dev.knowledgeprofiling.traceability.connector.SourceConnector;
import dev.knowledgeprofiling.traceability.connector.TraceSourceDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TraceabilityService {
    private final JdbcTemplate jdbc;
    private final VectorStore vectors;
    private final ChunkingService chunking;
    private final RagService rag;
    private final List<SourceConnector> connectors;
    private final Path generatedDocs;
    private final KnowledgeSearch lexicalSearch = new KnowledgeSearch();

    public TraceabilityService(JdbcTemplate jdbc, VectorStore vectors, ChunkingService chunking, RagService rag,
                               List<SourceConnector> connectors,
                               @Value("${app.traceability.generated-docs:docs/generated}") String generatedDocs) {
        this.jdbc = jdbc; this.vectors = vectors; this.chunking = chunking; this.rag = rag;
        this.connectors = List.copyOf(connectors); this.generatedDocs = Path.of(generatedDocs);
    }

    @Transactional
    public ReindexResult reindex(List<String> selectedConnectors) {
        int changed = 0, unchanged = 0;
        List<String> connectorNames = selectedConnectors == null || selectedConnectors.isEmpty()
                ? connectors.stream().map(SourceConnector::name).toList() : selectedConnectors;
        for (String name : connectorNames) {
            SourceConnector connector = connectors.stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown connector: " + name));
            List<TraceSourceDocument> fetched = connector.fetch();
            for (TraceSourceDocument source : fetched) {
                if (index(source)) changed++; else unchanged++;
            }
            jdbc.update("INSERT INTO trace_connector_state(connector,last_success_at,last_item_count) VALUES (?,CURRENT_TIMESTAMP,?) " +
                    "ON CONFLICT(connector) DO UPDATE SET last_success_at=CURRENT_TIMESTAMP,last_item_count=EXCLUDED.last_item_count", name, fetched.size());
        }
        rebuildLinks();
        generateModuleDocs();
        return new ReindexResult(changed, unchanged, connectorNames);
    }

    private boolean index(TraceSourceDocument source) {
        UUID id = UUID.nameUUIDFromBytes((source.sourceType() + ":" + source.externalId()).getBytes(StandardCharsets.UTF_8));
        String hash = sha256(source.content() + "\n" + source.title() + "\n" + source.path() + "\n" + source.url());
        List<String> oldHash = jdbc.query("SELECT content_hash FROM trace_entity WHERE id=?", (rs, row) -> rs.getString(1), id);
        if (!oldHash.isEmpty() && oldHash.getFirst().equals(hash)) return false;
        if (!oldHash.isEmpty()) {
            List<String> oldVectors = jdbc.query("SELECT vector_id FROM trace_vector_chunk WHERE entity_id=?", (rs, row) -> rs.getString(1), id);
            if (!oldVectors.isEmpty()) vectors.delete(oldVectors);
            jdbc.update("DELETE FROM trace_entity WHERE id=?", id);
        }
        jdbc.update("INSERT INTO trace_entity(id,source_type,external_id,repository,title,path,url,content,content_hash,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, source.sourceType(), source.externalId(), source.repository(), source.title(), source.path(), source.url(), source.content(), hash, source.updatedAt());
        List<Document> docs = chunking.chunk(source.sourceType(), id, source.title(), source.path(), source.url(),
                        List.of(new ExtractedPage(null, source.content()))).stream()
                .map(chunk -> {
                    var meta = chunk.metadata();
                    return new Document(UUID.randomUUID().toString(), chunk.content(), Map.of(
                            "source_type", meta.sourceType(), "source_id", meta.sourceId().toString(), "title", meta.title(),
                            "path", meta.path(), "url", meta.url() == null ? "" : meta.url(), "page", "", "line", meta.line()));
                }).toList();
        if (!docs.isEmpty()) {
            vectors.add(docs);
            for (int i = 0; i < docs.size(); i++) jdbc.update("INSERT INTO trace_vector_chunk(entity_id,chunk_index,vector_id) VALUES (?,?,?)", id, i, docs.get(i).getId());
        }
        return true;
    }

    private void rebuildLinks() {
        jdbc.update("DELETE FROM trace_link");
        List<Entity> entities = jdbc.query("SELECT id,source_type,external_id,repository,title,path,content FROM trace_entity", this::entity);
        Map<String, Entity> byExternalId = new LinkedHashMap<>();
        entities.forEach(entity -> byExternalId.put(entity.externalId, entity));
        for (Entity entity : entities) {
            for (String issue : issueReferences(entity.content)) link(byExternalId, entity, entity.repository + "#issue:" + issue, "REFERENCES_TICKET", "Référence de ticket présente dans le message ou le contenu source.");
            var commitMatcher = java.util.regex.Pattern.compile("Commit ([a-f0-9]{7,40}):").matcher(entity.content);
            while (commitMatcher.find()) link(byExternalId, entity, entity.repository + "#commit:" + commitMatcher.group(1), "CONTAINS_COMMIT", "Commit associé par l’API GitHub à cette pull request.");
            if (entity.type.equals("github-pr") || entity.type.equals("github-commit")) {
                for (Entity file : entities) if (file.type.equals("github-file") && entity.content.contains(file.path))
                    link(byExternalId, entity, file.externalId, "CHANGES_FILE", "Le chemin du fichier apparaît dans le contenu de la source.");
            }
            if (entity.type.equals("markdown")) for (Entity file : entities) if (file.type.equals("github-file") && entity.content.contains(file.path))
                link(byExternalId, entity, file.externalId, "DOCUMENTS_FILE", "La page Markdown mentionne le chemin du fichier.");
        }
    }

    private void link(Map<String, Entity> entities, Entity from, String target, String relation, String reason) {
        Entity to = entities.get(target);
        if (to != null && !to.id.equals(from.id)) jdbc.update("INSERT INTO trace_link(repository,from_entity,to_entity,relationship,justification) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",
                from.repository, from.id, to.id, relation, reason);
    }
    private List<String> issueReferences(String content) {
        var matcher = java.util.regex.Pattern.compile("(?i)(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\\s+#(\\d+)|#(\\d+)").matcher(content);
        List<String> refs = new ArrayList<>();
        while (matcher.find()) refs.add(matcher.group(1) == null ? matcher.group(2) : matcher.group(1));
        return refs;
    }

    @Transactional(readOnly = true)
    public FileHistory history(String path) {
        if (path == null || path.isBlank() || path.length() > 2000) throw new IllegalArgumentException("A valid file path is required");
        List<Entity> files = jdbc.query("SELECT id,source_type,external_id,repository,title,path,content FROM trace_entity WHERE path=? OR external_id LIKE ?", this::entity, path, "%#file:" + path);
        if (files.isEmpty()) return new FileHistory(path, List.of());
        Entity file = files.getFirst();
        List<TraceItem> items = jdbc.query("SELECT DISTINCT e.id,e.source_type,e.external_id,e.repository,e.title,e.path,e.content,e.url,l2.relationship,l2.justification " +
                "FROM trace_link l1 JOIN trace_link l2 ON l2.from_entity=CASE WHEN l1.from_entity=? THEN l1.to_entity ELSE l1.from_entity END " +
                "OR l2.to_entity=CASE WHEN l1.from_entity=? THEN l1.to_entity ELSE l1.from_entity END " +
                "JOIN trace_entity e ON e.id=CASE WHEN l2.from_entity=CASE WHEN l1.from_entity=? THEN l1.to_entity ELSE l1.from_entity END THEN l2.to_entity ELSE l2.from_entity END " +
                "WHERE (l1.from_entity=? OR l1.to_entity=?) AND e.id<>? ORDER BY e.source_type,e.title",
                this::traceItem, file.id, file.id, file.id, file.id, file.id, file.id);
        return new FileHistory(path, items);
    }

    @Transactional(readOnly = true)
    public ImpactResult impact(List<String> paths, List<Integer> pullRequests) {
        List<ImpactItem> results = new ArrayList<>();
        if (paths != null) for (String path : paths) {
            FileHistory history = history(path);
            for (TraceItem item : history.related()) results.add(new ImpactItem(path, item, "Lien explicite enregistré entre le fichier et cette source."));
        }
        if (pullRequests != null) for (Integer number : pullRequests) {
            String key = "%#pr:" + number;
            List<TraceItem> related = jdbc.query("SELECT DISTINCT e.id,e.source_type,e.external_id,e.repository,e.title,e.path,e.content,e.url,l.relationship,l.justification FROM trace_entity p " +
                    "JOIN trace_link l ON l.from_entity=p.id OR l.to_entity=p.id JOIN trace_entity e ON e.id=CASE WHEN l.from_entity=p.id THEN l.to_entity ELSE l.from_entity END " +
                    "WHERE p.external_id LIKE ?", this::traceItem, key);
            for (TraceItem item : related) if (item.sourceType().equals("github-file")) results.add(new ImpactItem(item.path(), item, "Fichier relié à la PR par l’historique indexé."));
        }
        return new ImpactResult(results.stream().distinct().toList());
    }

    public TraceAskResult ask(String question, Integer topK) {
        RagAnswer answer = rag.query(question, topK);
        List<TraceItem> linked = new ArrayList<>();
        for (var source : answer.sources()) {
            try {
                UUID id = UUID.fromString(source.sourceId());
                linked.addAll(jdbc.query("SELECT DISTINCT e.id,e.source_type,e.external_id,e.repository,e.title,e.path,e.content,e.url,l.relationship,l.justification FROM trace_link l JOIN trace_entity e ON e.id=CASE WHEN l.from_entity=? THEN l.to_entity ELSE l.from_entity END WHERE l.from_entity=? OR l.to_entity=?", this::traceItem, id, id, id));
            } catch (IllegalArgumentException ignored) { /* Uploaded RAG documents need not have a trace entity. */ }
        }
        return new TraceAskResult(answer, linked.stream().distinct().toList());
    }

    public List<SourceDocument> findEvidence(String query, List<SourceDocument> corpus) { return lexicalSearch.search(query, corpus); }

    public void generateModuleDocs() {
        try {
            List<String> modules = jdbc.query("SELECT DISTINCT repository || '|' || CASE WHEN position('/' in path)>0 THEN split_part(path,'/',1) || '/' || split_part(path,'/',2) ELSE path END " +
                    "FROM trace_entity WHERE source_type='github-file' AND repository<>''", (rs, row) -> rs.getString(1));
            Files.createDirectories(generatedDocs);
            for (String module : modules) {
                String[] parts = module.split("\\|", 2);
                String repository = parts[0], modulePath = parts[1];
                StringBuilder md = new StringBuilder("# Sources générées — ").append(modulePath).append("\n\nDépôt : ").append(repository).append(". Généré automatiquement depuis le code et les tickets indexés.\n");
                List<Entity> code = jdbc.query("SELECT id,source_type,external_id,repository,title,path,content FROM trace_entity WHERE repository=? AND source_type='github-file' AND path LIKE ? ORDER BY path", this::entity, repository, modulePath + "/%");
                for (Entity file : code) {
                    md.append("\n## ").append(file.path).append("\n\n");
                    if (file.content.length() > 500) md.append(file.content, 0, 500).append("…\n"); else md.append(file.content).append('\n');
                    List<String> tickets = jdbc.query("SELECT DISTINCT e.title || ' (' || e.external_id || ')' FROM trace_link l JOIN trace_entity e ON e.id=CASE WHEN l.from_entity=? THEN l.to_entity ELSE l.from_entity END WHERE (l.from_entity=? OR l.to_entity=?) AND e.source_type='github-issue'", (rs, row) -> rs.getString(1), file.id, file.id, file.id);
                    if (!tickets.isEmpty()) md.append("Tickets liés : ").append(String.join(", ", tickets)).append('\n');
                }
                Files.writeString(generatedDocs.resolve(modulePath.replace('/', '-') + ".md"), md);
            }
        } catch (Exception e) { throw new IllegalStateException("Unable to generate traceability Markdown documentation", e); }
    }

    private Entity entity(ResultSet rs, int row) throws SQLException {
        return new Entity(rs.getObject("id", UUID.class), rs.getString("source_type"), rs.getString("external_id"),
                rs.getString("repository"), rs.getString("title"), rs.getString("path"), rs.getString("content"), null);
    }
    private TraceItem traceItem(ResultSet rs, int row) throws SQLException {
        return new TraceItem(rs.getString("source_type"), rs.getString("external_id"), rs.getString("repository"), rs.getString("title"), rs.getString("path"), rs.getString("url"), rs.getString("content"), rs.getString("relationship"), rs.getString("justification"));
    }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private record Entity(UUID id, String type, String externalId, String repository, String title, String path, String content, String url) { }
    public record ReindexResult(int changed, int unchanged, List<String> connectors) { }
    public record TraceItem(String sourceType, String externalId, String repository, String title, String path, String url, String excerpt, String relationship, String justification) { }
    public record FileHistory(String path, List<TraceItem> related) { }
    public record ImpactItem(String path, TraceItem source, String justification) { }
    public record ImpactResult(List<ImpactItem> impacted) { }
    public record TraceAskResult(RagAnswer answer, List<TraceItem> relatedSources) { }
}

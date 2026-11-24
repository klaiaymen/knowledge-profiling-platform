package dev.knowledgeprofiling.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class IngestionService {
    public static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    private final VectorStore vectorStore;
    private final List<DocumentSource> sources;
    private final ChunkingService chunking;

    public IngestionService(VectorStore vectorStore, List<DocumentSource> sources, ChunkingService chunking) {
        this.vectorStore = vectorStore;
        this.sources = sources;
        this.chunking = chunking;
    }

    public IngestionResult ingest(String filename, String contentType, byte[] content, String url) {
        if (filename == null || filename.isBlank()) throw new IllegalArgumentException("A filename is required");
        if (content == null || content.length == 0) throw new IllegalArgumentException("Uploaded document is empty");
        if (content.length > MAX_UPLOAD_BYTES) throw new IllegalArgumentException("Uploaded document exceeds 10 MB");
        DocumentSource source = sources.stream().filter(s -> s.supports(filename, contentType)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported document type"));
        try {
            List<ExtractedPage> pages = source.extract(content);
            UUID sourceId = UUID.randomUUID();
            List<DocumentChunk> chunks = chunking.chunk(source.sourceType(), sourceId, filename, filename, url, pages);
            if (chunks.isEmpty()) throw new IllegalArgumentException("Document contains no extractable text");
            List<Document> vectorDocuments = new ArrayList<>(chunks.size());
            for (DocumentChunk chunk : chunks) {
                vectorDocuments.add(new Document(chunk.content(), toMetadata(chunk.metadata())));
            }
            vectorStore.add(vectorDocuments);
            return new IngestionResult(sourceId, filename, source.sourceType(), chunks.size());
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not extract uploaded document", e);
        }
    }

    private LinkedHashMap<String, Object> toMetadata(SourceMetadata metadata) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("source_type", metadata.sourceType());
        values.put("source_id", metadata.sourceId().toString());
        values.put("title", metadata.title());
        values.put("path", metadata.path());
        if (metadata.page() != null) values.put("page", metadata.page());
        if (metadata.line() != null) values.put("line", metadata.line());
        if (metadata.url() != null && !metadata.url().isBlank()) values.put("url", metadata.url());
        return values;
    }

    public record IngestionResult(UUID sourceId, String title, String sourceType, int chunks) { }
}

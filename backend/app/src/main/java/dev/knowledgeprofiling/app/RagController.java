package dev.knowledgeprofiling.app;

import dev.knowledgeprofiling.rag.IngestionService;
import dev.knowledgeprofiling.rag.RagAnswer;
import dev.knowledgeprofiling.rag.RagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import java.io.IOException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/rag")
public class RagController {
    private final RagService ragService;
    private final IngestionService ingestionService;
    private final AuditService auditService;

    public RagController(RagService ragService, IngestionService ingestionService, AuditService auditService) {
        this.ragService = ragService;
        this.ingestionService = ingestionService;
        this.auditService = auditService;
    }

    @PostMapping("/query")
    public RagAnswer query(@Valid @RequestBody QueryRequest request, Authentication authentication) {
        try {
            RagAnswer answer = ragService.query(request.question(), request.topK());
            auditService.record(authentication.getName(), "RAG_QUERY", "rag:query");
            return answer;
        }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<RagAnswer>> stream(@RequestParam(name = "question") String question,
                                                    @RequestParam(name = "topK", required = false) Integer topK,
                                                    Authentication authentication) {
        try {
            auditService.record(authentication.getName(), "RAG_QUERY_STREAM", "rag:stream");
            return ragService.stream(question, topK).map(answer -> ServerSentEvent.builder(answer).event("answer").build());
        }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestionService.IngestionResult upload(@RequestPart("file") MultipartFile file,
                                                    @RequestParam(name = "url", required = false) String url,
                                                    Authentication authentication) {
        String filename = safeFilename(file.getOriginalFilename());
        if (file.getSize() > IngestionService.MAX_UPLOAD_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Maximum upload size is 10 MB");
        if (url != null && !url.isBlank()) validateSourceUrl(url);
        try {
            IngestionService.IngestionResult result = ingestionService.ingest(filename, file.getContentType(), file.getBytes(), url);
            auditService.record(authentication.getName(), "RAG_DOCUMENT_INGEST", result.sourceId().toString());
            return result;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded file");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private String safeFilename(String original) {
        if (original == null || original.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A filename is required");
        String normalized = original.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (filename.length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename is too long");
        return filename;
    }

    private void validateSourceUrl(String value) {
        if (value.length() > 2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source URL is too long");
        try {
            String scheme = java.net.URI.create(value).getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source URL must use HTTP or HTTPS");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source URL is invalid");
        }
    }

    public record QueryRequest(@NotBlank @Size(max = 2000) String question, @Min(1) @Max(20) Integer topK) { }
}

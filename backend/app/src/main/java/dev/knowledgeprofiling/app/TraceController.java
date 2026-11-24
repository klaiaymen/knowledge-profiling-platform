package dev.knowledgeprofiling.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knowledgeprofiling.traceability.TraceabilityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@RestController
@RequestMapping("/api/trace")
public class TraceController {
    private final TraceabilityService traceability;
    private final AuditService audit;
    private final String webhookSecret;
    private final String githubRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public TraceController(TraceabilityService traceability, AuditService audit,
                           @Value("${GITHUB_WEBHOOK_SECRET:}") String webhookSecret,
                           @Value("${KPF_GITHUB_REPOSITORY:}") String githubRepository) {
        this.traceability = traceability; this.audit = audit; this.webhookSecret = webhookSecret; this.githubRepository = githubRepository;
    }

    @PostMapping("/reindex")
    public TraceabilityService.ReindexResult reindex(@RequestBody(required = false) ReindexRequest request, Authentication auth) {
        try {
            var result = traceability.reindex(request == null ? List.of() : request.connectors());
            audit.record(auth.getName(), "TRACE_REINDEX", "trace:index");
            return result;
        } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
    }

    @PostMapping("/webhook")
    public TraceabilityService.ReindexResult webhook(@RequestBody String payload,
                                                      @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (!validSignature(payload, signature)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        try {
            JsonNode root = mapper.readTree(payload);
            String repository = root.path("repository").path("full_name").asText();
            if (!githubRepository.isBlank() && !githubRepository.equalsIgnoreCase(repository))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Webhook repository is not configured");
            return traceability.reindex(List.of("github"));
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook payload"); }
    }

    @GetMapping("/file")
    public TraceabilityService.FileHistory file(@RequestParam("path") @NotBlank @Size(max = 2000) String path, Authentication auth) {
        audit.record(auth.getName(), "TRACE_FILE_HISTORY", "trace:file");
        try { return traceability.history(path); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
    }

    @PostMapping("/impact")
    public TraceabilityService.ImpactResult impact(@Valid @RequestBody ImpactRequest request, Authentication auth) {
        audit.record(auth.getName(), "TRACE_IMPACT", "trace:impact");
        return traceability.impact(request.paths(), request.pullRequests());
    }

    @PostMapping("/ask")
    public TraceabilityService.TraceAskResult ask(@Valid @RequestBody AskRequest request, Authentication auth) {
        audit.record(auth.getName(), "TRACE_ASK", "trace:ask");
        try { return traceability.ask(request.question(), request.topK()); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
    }

    private boolean validSignature(String payload, String signature) {
        if (webhookSecret.isBlank() || signature == null || !signature.matches("sha256=[a-fA-F0-9]{64}")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) { throw new IllegalStateException("Could not verify webhook signature", e); }
    }

    public record ReindexRequest(List<String> connectors) { }
    public record ImpactRequest(@Size(max = 100) List<@Size(max = 2000) String> paths, @Size(max = 100) List<@Max(1_000_000) Integer> pullRequests) { }
    public record AskRequest(@NotBlank @Size(max = 2000) String question, @Max(20) Integer topK) { }
}

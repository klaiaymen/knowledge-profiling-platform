package dev.knowledgeprofiling.app;

import dev.knowledgeprofiling.profiling.AnswerJsonExtractor;
import dev.knowledgeprofiling.profiling.InvestorAnswers;
import dev.knowledgeprofiling.profiling.RiskScoringService;
import dev.knowledgeprofiling.rag.SourceDocument;
import dev.knowledgeprofiling.traceability.TraceabilityService;
import dev.knowledgeprofiling.traceability.connector.SourceConnector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final RiskScoringService scoring = new RiskScoringService();
    private final AnswerJsonExtractor extractor = new AnswerJsonExtractor();
    private final TraceabilityService traceability;
    private final List<SourceConnector> sourceConnectors;
    private final ObjectProvider<ConversationService> conversationService;

    public ApiController(
            TraceabilityService traceability,
            List<SourceConnector> sourceConnectors,
            ObjectProvider<ConversationService> conversationService) {
        this.traceability = traceability;
        this.sourceConnectors = sourceConnectors;
        this.conversationService = conversationService;
    }

    @GetMapping("/health") public HealthResponse health() { return new HealthResponse("ok"); }
    @GetMapping("/search") public List<SourceDocument> search(@RequestParam("q") String q) {
        var sources = sourceConnectors.stream().flatMap(connector -> connector.fetch().stream())
                .map(source -> new SourceDocument(source.externalId(), source.title(), source.url() == null ? source.path() : source.url(), source.content(), source.sourceType()))
                .toList();
        return traceability.findEvidence(q, sources);
    }
    @PostMapping("/profiling/score") public Object score(@RequestBody String answersJson) {
        InvestorAnswers answers = extractor.extract(answersJson);
        return scoring.score(answers);
    }
    @PostMapping("/profiling/turn") public ConversationService.TurnResult turn(@RequestBody TurnRequest request) {
        ConversationService service = conversationService.getIfAvailable();
        if (service == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No LLM provider is configured");
        try { return service.next(request.conversation(), request.message()); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The model returned an invalid interview response"); }
    }
    @GetMapping("/admin/status") public HealthResponse adminStatus() { return new HealthResponse("admin"); }

    public record TurnRequest(String conversation, String message) { }
    public record HealthResponse(String status) { }
}

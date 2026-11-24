package dev.knowledgeprofiling.app;

import dev.knowledgeprofiling.profiling.ProfilePdfExporter;
import dev.knowledgeprofiling.profiling.ProfilingSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiling")
public class ProfilingController {
    private final ProfilingSessionService sessions;
    private final ProfilePdfExporter pdfExporter;

    public ProfilingController(ProfilingSessionService sessions, ProfilePdfExporter pdfExporter) {
        this.sessions = sessions; this.pdfExporter = pdfExporter;
    }

    @PostMapping("/sessions")
    public ProfilingSessionService.InterviewTurn create(Authentication auth) {
        return sessions.create(auth.getName());
    }

    @PostMapping(value = "/sessions/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ProfilingSessionService.InterviewTurn>> message(
            @PathVariable("id") UUID id, @Valid @RequestBody MessageRequest request, Authentication auth) {
        try { return Flux.just(ServerSentEvent.builder(sessions.send(auth.getName(), id, request.message())).event("turn").build()); }
        catch (ProfilingSessionService.SessionNotFoundException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"); }
        catch (ProfilingSessionService.InvalidExtractionException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The model returned invalid structured answers"); }
        catch (ChatClientInterviewModel.InterviewUnavailableException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No interview model is configured"); }
        catch (IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage()); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
    }

    @GetMapping("/sessions/{id}/profile")
    public ProfilingSessionService.ProfileReport profile(@PathVariable("id") UUID id, Authentication auth) {
        try { return sessions.profile(auth.getName(), id); }
        catch (ProfilingSessionService.SessionNotFoundException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"); }
        catch (IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage()); }
    }

    @GetMapping(value = "/sessions/{id}/profile.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> profilePdf(@PathVariable("id") UUID id, Authentication auth) {
        try {
            byte[] pdf = pdfExporter.export(sessions.profile(auth.getName(), id));
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=convi-fin-profile.pdf")
                    .contentType(MediaType.APPLICATION_PDF).body(pdf);
        } catch (ProfilingSessionService.SessionNotFoundException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"); }
        catch (IllegalStateException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage()); }
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id, Authentication auth) {
        try { sessions.delete(auth.getName(), id); }
        catch (ProfilingSessionService.SessionNotFoundException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"); }
    }

    @GetMapping("/dashboard")
    public ProfilingSessionService.Dashboard dashboard() { return sessions.dashboard(); }

    public record MessageRequest(@NotBlank @Size(max = 2000) String message) { }
}

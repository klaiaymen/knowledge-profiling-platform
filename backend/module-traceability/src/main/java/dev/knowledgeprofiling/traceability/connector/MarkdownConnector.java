package dev.knowledgeprofiling.traceability.connector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

@Component
@Order(1)
public class MarkdownConnector implements SourceConnector {
    private final Path root;
    @Autowired
    public MarkdownConnector(@Value("${app.docs.path:docs/sources}") String root) { this.root = Path.of(root); }
    public MarkdownConnector(Path root) { this.root = root; }
    @Override public String name() { return "markdown"; }

    @Override public List<TraceSourceDocument> fetch() {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase().endsWith(".md"))
                    .map(this::read).toList();
        } catch (IOException e) { throw new IllegalStateException("Unable to read Markdown source directory", e); }
    }

    private TraceSourceDocument read(Path file) {
        try {
            String path = root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
            return new TraceSourceDocument("markdown", path, "", file.getFileName().toString(), path, null,
                    Files.readString(file), Files.getLastModifiedTime(file).toInstant(), List.of(), List.of());
        } catch (IOException e) { throw new IllegalStateException("Unable to read Markdown document", e); }
    }
}

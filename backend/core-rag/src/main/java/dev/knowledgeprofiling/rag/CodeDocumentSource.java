package dev.knowledgeprofiling.rag;

import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@Order(1)
public class CodeDocumentSource implements DocumentSource {
    private static final Set<String> EXTENSIONS = Set.of("java", "kt", "py", "js", "jsx", "ts", "tsx", "go", "rs", "c", "h", "cpp", "hpp", "cs", "rb", "php", "sql", "xml", "json", "yml", "yaml", "sh", "css", "html");
    @Override public boolean supports(String filename, String contentType) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(filename.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
    @Override public String sourceType() { return "code"; }
    @Override public List<ExtractedPage> extract(byte[] content) {
        return List.of(new ExtractedPage(null, new String(content, StandardCharsets.UTF_8)));
    }
}

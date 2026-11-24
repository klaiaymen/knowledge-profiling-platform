package dev.knowledgeprofiling.rag;

import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Order(2)
public class TextDocumentSource implements DocumentSource {
    @Override public boolean supports(String filename, String contentType) {
        return filename.toLowerCase().endsWith(".txt") || "text/plain".equalsIgnoreCase(contentType);
    }
    @Override public String sourceType() { return "text"; }
    @Override public List<ExtractedPage> extract(byte[] content) {
        return List.of(new ExtractedPage(null, new String(content, StandardCharsets.UTF_8)));
    }
}

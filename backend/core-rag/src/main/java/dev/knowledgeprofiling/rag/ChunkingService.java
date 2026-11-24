package dev.knowledgeprofiling.rag;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ChunkingService {
    private final int maxCharacters;
    private final int overlapCharacters;

    public ChunkingService() { this(1200, 160); }
    ChunkingService(int maxCharacters, int overlapCharacters) {
        this.maxCharacters = maxCharacters;
        this.overlapCharacters = overlapCharacters;
    }

    public List<DocumentChunk> chunk(String sourceType, UUID sourceId, String title, String path, String url,
                                     List<ExtractedPage> pages) {
        List<DocumentChunk> chunks = new ArrayList<>();
        for (ExtractedPage page : pages) {
            String text = page.text();
            int start = 0;
            int line = 1;
            while (start < text.length()) {
                int end = Math.min(start + maxCharacters, text.length());
                if (end < text.length()) {
                    int newline = text.lastIndexOf('\n', end - 1);
                    if (newline > start) end = newline + 1;
                }
                String chunk = text.substring(start, end).strip();
                int lineAtStart = line;
                for (int i = start; i < end; i++) if (text.charAt(i) == '\n') line++;
                int next = Math.max(start + 1, end - overlapCharacters);
                for (int i = next; i < end; i++) if (text.charAt(i) == '\n') line--;
                if (end == text.length()) next = end;
                if (!chunk.isBlank()) {
                    chunks.add(new DocumentChunk(chunk, new SourceMetadata(sourceType, sourceId, title, path,
                            page.page(), lineAtStart, url)));
                }
                if (next == text.length()) break;
                start = next;
            }
        }
        return List.copyOf(chunks);
    }
}

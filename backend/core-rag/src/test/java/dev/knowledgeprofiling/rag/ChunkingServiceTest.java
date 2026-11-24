package dev.knowledgeprofiling.rag;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChunkingServiceTest {
    @Test void chunksRetainSourceMetadataAndStartingLine() {
        UUID id = UUID.randomUUID();
        var chunks = new ChunkingService(12, 3).chunk("markdown", id, "guide.md", "docs/guide.md", "https://example.test/guide",
                List.of(new ExtractedPage(null, "first line\nsecond line\nthird line\nfourth line")));
        assertTrue(chunks.size() > 1);
        assertEquals(id, chunks.getFirst().metadata().sourceId());
        assertEquals("docs/guide.md", chunks.getFirst().metadata().path());
        assertNotNull(chunks.get(1).metadata().line());
        assertEquals("https://example.test/guide", chunks.getFirst().metadata().url());
    }

    @Test void longSingleLinesAreStillSplit() {
        String content = "a".repeat(120);
        var chunks = new ChunkingService(20, 4).chunk("code", UUID.randomUUID(), "long.java", "long.java", null,
                List.of(new ExtractedPage(null, content)));
        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.content().length() <= 20));
    }
}

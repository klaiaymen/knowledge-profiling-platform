package dev.knowledgeprofiling.app;

import dev.knowledgeprofiling.rag.ChunkingService;
import dev.knowledgeprofiling.rag.MarkdownDocumentSource;
import dev.knowledgeprofiling.rag.IngestionService;
import dev.knowledgeprofiling.rag.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class RagPgVectorIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Test void ingestsMarkdownSearchesPgvectorAndAbstainsOffTopic() throws Exception {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS hstore");
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
        DeterministicEmbeddingModel embeddings = new DeterministicEmbeddingModel();
        PgVectorStore vectorStore = PgVectorStore.builder(jdbc, embeddings).dimensions(384).initializeSchema(true).build();
        vectorStore.afterPropertiesSet();
        FixedChatModel fakeModel = new FixedChatModel();
        IngestionService ingestion = new IngestionService(vectorStore, List.of(new MarkdownDocumentSource()), new ChunkingService());
        String markdown = "# Risk horizon policy\nThe risk horizon rule requires a long-term investment horizon before choosing this strategy.\n";
        var uploaded = ingestion.ingest("risk-policy.md", "text/markdown", markdown.getBytes(StandardCharsets.UTF_8), "https://example.test/risk-policy");
        RagService rag = new RagService(vectorStore, fakeModel, 5, 0.25);

        var answer = rag.query("What does the risk horizon rule require?", null);
        assertEquals(1, uploaded.chunks());
        assertEquals("MEDIUM", answer.confidence());
        assertTrue(answer.answer().contains("[S1]"));
        assertEquals("risk-policy.md", answer.sources().getFirst().path());
        assertEquals("https://example.test/risk-policy", answer.sources().getFirst().url());
        assertTrue(answer.sources().getFirst().excerpt().contains("risk horizon rule"));

        var offTopic = rag.query("Who won the intergalactic spaceship race?", null);
        assertEquals(RagService.NOT_FOUND, offTopic.answer());
        assertEquals("LOW", offTopic.confidence());
        assertTrue(offTopic.sources().isEmpty());
        assertEquals(1, fakeModel.calls);
    }

    private static final class FixedChatModel implements ChatModel {
        private int calls;
        @Override public ChatResponse call(Prompt prompt) {
            calls++;
            return new ChatResponse(List.of(new Generation(new AssistantMessage("The rule requires a long-term investment horizon. [S1]"))));
        }
        @Override public ChatOptions getDefaultOptions() { return null; }
    }

    private static final class DeterministicEmbeddingModel implements EmbeddingModel {
        @Override public float[] embed(Document document) { return vector(document.getText()); }
        @Override public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> results = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) results.add(new Embedding(vector(request.getInstructions().get(i)), i));
            return new EmbeddingResponse(results);
        }
        @Override public int dimensions() { return 384; }
        private float[] vector(String text) {
            float[] values = new float[384];
            for (String token : text.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
                if (!token.isBlank()) values[Math.floorMod(token.hashCode(), values.length)] += 1;
            }
            double norm = Math.sqrt(java.util.Arrays.stream(toDouble(values)).map(v -> v * v).sum());
            if (norm > 0) for (int i = 0; i < values.length; i++) values[i] /= (float) norm;
            return values;
        }
        private double[] toDouble(float[] values) {
            double[] result = new double[values.length];
            for (int i = 0; i < values.length; i++) result[i] = values[i];
            return result;
        }
    }
}

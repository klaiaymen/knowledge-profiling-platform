package dev.knowledgeprofiling.app;

import dev.knowledgeprofiling.rag.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RagServiceTest {
    @Test void returnsOnlyExactRetrievedSourcesAndCitedAnswer() {
        VectorStore store = mock(VectorStore.class);
        ChatModel model = mock(ChatModel.class);
        Document source = new Document("Risk horizon is at least five years.", Map.of(
                "source_type", "markdown", "source_id", "doc-1", "title", "Policy", "path", "policy.md", "line", 4));
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(source));
        when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("The horizon is five years. [S1]")))));

        var answer = new RagService(store, model, 5, 0.55).query("What is the risk horizon?", null);

        assertEquals("The horizon is five years. [S1]", answer.answer());
        assertEquals("Policy", answer.sources().getFirst().title());
        assertEquals("policy.md", answer.sources().getFirst().path());
        assertEquals("Risk horizon is at least five years.", answer.sources().getFirst().excerpt());
    }

    @Test void abstainsWithoutCallingModelWhenNoPassageIsRelevant() {
        VectorStore store = mock(VectorStore.class);
        ChatModel model = mock(ChatModel.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        var answer = new RagService(store, model, 5, 0.55).query("Who won a fictional space race?", null);
        assertEquals(RagService.NOT_FOUND, answer.answer());
        assertEquals("LOW", answer.confidence());
        assertTrue(answer.sources().isEmpty());
        verifyNoInteractions(model);
    }

    @Test void rejectsFabricatedCitationIdentifiers() {
        VectorStore store = mock(VectorStore.class);
        ChatModel model = mock(ChatModel.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document("evidence")));
        when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Unsupported answer [S9]")))));
        var answer = new RagService(store, model, 5, 0.55).query("Question?", null);
        assertEquals(RagService.NOT_FOUND, answer.answer());
        assertEquals("LOW", answer.confidence());
        assertTrue(answer.sources().isEmpty());
    }
}

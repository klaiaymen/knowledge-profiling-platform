package dev.knowledgeprofiling.rag;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RagService {
    public static final String NOT_FOUND = "information non trouvée dans les sources";
    private static final Pattern CITATION = Pattern.compile("\\[S(\\d+)]");
    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final int defaultTopK;
    private final double similarityThreshold;

    public RagService(VectorStore vectorStore, ChatModel chatModel,
                      @Value("${app.rag.top-k:5}") int defaultTopK,
                      @Value("${app.rag.similarity-threshold:0.55}") double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.defaultTopK = defaultTopK;
        this.similarityThreshold = similarityThreshold;
    }

    public RagAnswer query(String question, Integer requestedTopK) {
        if (question == null || question.isBlank() || question.length() > 2000)
            throw new IllegalArgumentException("Question must contain 1 to 2000 characters");
        int topK = requestedTopK == null ? defaultTopK : requestedTopK;
        if (topK < 1 || topK > 20) throw new IllegalArgumentException("topK must be between 1 and 20");
        List<Document> matches = vectorStore.similaritySearch(SearchRequest.builder().query(question)
                .topK(topK).similarityThreshold(similarityThreshold).build());
        List<RagSource> sources = toSources(matches);
        if (sources.isEmpty()) return notFound();
        String generated = chatModel.call(prompt(question, sources)).getResult().getOutput().getText();
        String answer = onlyCitedAnswer(generated, sources.size());
        if (answer.equals(NOT_FOUND)) return new RagAnswer(answer, "LOW", List.of());
        Double score = matches.getFirst().getScore();
        String confidence = score != null && score >= 0.78 ? "HIGH" : "MEDIUM";
        return new RagAnswer(answer, confidence, sources);
    }

    public Flux<RagAnswer> stream(String question, Integer requestedTopK) {
        if (question == null || question.isBlank() || question.length() > 2000)
            throw new IllegalArgumentException("Question must contain 1 to 2000 characters");
        int topK = requestedTopK == null ? defaultTopK : requestedTopK;
        if (topK < 1 || topK > 20) throw new IllegalArgumentException("topK must be between 1 and 20");
        List<Document> matches = vectorStore.similaritySearch(SearchRequest.builder().query(question)
                .topK(topK).similarityThreshold(similarityThreshold).build());
        List<RagSource> sources = toSources(matches);
        if (sources.isEmpty()) return Flux.just(notFound());
        return chatModel.stream(prompt(question, sources))
                .map(response -> response.getResult() == null ? "" : response.getResult().getOutput().getText())
                .reduce(new StringBuilder(), StringBuilder::append)
                .map(StringBuilder::toString)
                .map(answer -> {
                    String cited = onlyCitedAnswer(answer, sources.size());
                    if (cited.equals(NOT_FOUND)) return notFound();
                    Double score = matches.getFirst().getScore();
                    return new RagAnswer(cited, score != null && score >= 0.78 ? "HIGH" : "MEDIUM", sources);
                })
                .flux();
    }

    private Prompt prompt(String question, List<RagSource> sources) {
        String context = sources.stream().map(source -> "[" + source.citation() + "] " + source.title() +
                " — " + source.path() + (source.page() == null ? "" : ", page " + source.page()) +
                (source.line() == null ? "" : ", line " + source.line()) + "\n" + source.excerpt())
                .reduce((a, b) -> a + "\n\n" + b).orElse("");
        return new Prompt(List.of(
                new SystemMessage("Réponds uniquement à partir des passages fournis. Cite chaque affirmation avec " +
                        "un identifiant exact au format [S1]. Si les passages ne suffisent pas, réponds exactement : " + NOT_FOUND + ". N'invente ni faits, ni sources."),
                new UserMessage("Question: " + question + "\n\nPassages disponibles:\n" + context)));
    }

    private String onlyCitedAnswer(String generated, int sourceCount) {
        if (generated == null || generated.isBlank()) return NOT_FOUND;
        Matcher matcher = CITATION.matcher(generated);
        boolean hasValidCitation = false;
        StringBuilder sanitized = new StringBuilder();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index >= 1 && index <= sourceCount) {
                hasValidCitation = true;
                matcher.appendReplacement(sanitized, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(sanitized, "");
            }
        }
        matcher.appendTail(sanitized);
        return hasValidCitation ? sanitized.toString().trim() : NOT_FOUND;
    }

    private List<RagSource> toSources(List<Document> documents) {
        List<RagSource> sources = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            var metadata = document.getMetadata();
            sources.add(new RagSource("S" + (i + 1), document.getText(), value(metadata, "source_type"),
                    value(metadata, "source_id"), value(metadata, "title"), value(metadata, "path"),
                    integer(metadata.get("page")), integer(metadata.get("line")), value(metadata, "url")));
        }
        return List.copyOf(sources);
    }

    private String value(java.util.Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : value.toString();
    }
    private Integer integer(Object value) { return value instanceof Number number ? number.intValue() : null; }
    private RagAnswer notFound() { return new RagAnswer(NOT_FOUND, "LOW", List.of()); }
}

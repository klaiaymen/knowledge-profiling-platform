package dev.knowledgeprofiling.profiling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Component
public class RegulationKnowledgeBase {
    private final List<RegulatorySource> sources;

    public RegulationKnowledgeBase(@Value("${app.profiling.regulation-path:data/regulation}") String directory) {
        Path root = Path.of(directory);
        this.sources = List.of(
                load(root.resolve("ESMA.md"), new RegulatorySource("ESMA", "https://www.esma.europa.eu/publications-and-data/interactive-single-rulebook/mifid-ii/article-25-assessment-suitability-and", "evaluation adéquation connaissances expérience situation financière capacité pertes objectifs tolérance risque")),
                load(root.resolve("AMF.md"), new RegulatorySource("AMF", "https://www.amf-france.org/fr/pourquoi-mon-intermediaire-financier-me-demande-t-il-de-remplir-un-questionnaire-detaille-0", "questionnaire connaissances expérience situation financière objectifs investissement conseil")));
    }

    public Citation find(String query) {
        String normalized = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        return sources.stream().max(Comparator.comparingInt(source -> score(normalized, source.content())))
                .map(source -> new Citation(source.name(), source.url(), excerpt(source.content(), normalized)))
                .orElseThrow();
    }

    private int score(String query, String content) {
        int score = 0;
        for (String term : query.split("\\W+")) if (term.length() > 3 && content.toLowerCase(java.util.Locale.ROOT).contains(term)) score++;
        return score;
    }
    private String excerpt(String content, String query) {
        String[] paragraphs = content.split("\\R\\s*\\R");
        return java.util.Arrays.stream(paragraphs).filter(p -> score(query, p) > 0).findFirst()
                .orElseGet(() -> paragraphs.length > 0 ? paragraphs[0] : "Résumé public des facteurs d’adéquation.").strip();
    }
    private RegulatorySource load(Path path, RegulatorySource fallback) {
        try {
            if (Files.isRegularFile(path)) return new RegulatorySource(fallback.name(), fallback.url(), Files.readString(path));
            String resource = "/data/regulation/" + path.getFileName();
            try (var input = RegulationKnowledgeBase.class.getResourceAsStream(resource)) {
                if (input != null) return new RegulatorySource(fallback.name(), fallback.url(), new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) { /* Use the short built-in fallback in the packaged demo. */ }
        return fallback;
    }
    private record RegulatorySource(String name, String url, String content) { }
    public record Citation(String authority, String url, String excerpt) { }
}

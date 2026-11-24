package dev.knowledgeprofiling.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knowledgeprofiling.profiling.AnswerJsonExtractor;
import dev.knowledgeprofiling.profiling.InvestorAnswers;
import dev.knowledgeprofiling.profiling.InvestorProfile;
import dev.knowledgeprofiling.profiling.RiskScoringService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(ChatClient.Builder.class)
public class ConversationService {
    private final ChatClient chatClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AnswerJsonExtractor extractor = new AnswerJsonExtractor();
    private final RiskScoringService scoring = new RiskScoringService();

    public ConversationService(ChatClient.Builder builder) { this.chatClient = builder.build(); }

    public TurnResult next(String conversation, String userMessage) {
        if (userMessage == null || userMessage.isBlank() || userMessage.length() > 1000)
            throw new IllegalArgumentException("Interview message must contain 1 to 1000 characters");
        if (conversation != null && conversation.length() > 8000)
            throw new IllegalArgumentException("Interview history is too long; restart the session");
        String output = chatClient.prompt()
                .system("Conduis un entretien pédagogique de profilage. Recueille l'horizon (1-40 ans), " +
                        "la tolérance aux pertes (1-5) et l'expérience (0-3). Réponds uniquement avec un objet JSON " +
                        "ayant les propriétés assistantMessage, horizonYears, lossTolerance, experience. " +
                        "Laisse les valeurs numériques à null si elles ne sont pas confirmées. Ne calcule jamais de score.")
                .user("Historique de cet entretien (synthétique, fourni uniquement pour cette session):\n" +
                        (conversation == null ? "" : conversation) + "\nNouvelle réponse:\n" + userMessage).call().content();
        try {
            ExtractedTurn parsed = mapper.readValue(output, ExtractedTurn.class);
            InvestorProfile profile = null;
            if (parsed.horizonYears() != null && parsed.lossTolerance() != null && parsed.experience() != null) {
                InvestorAnswers answers = extractor.extract(mapper.writeValueAsString(new InvestorAnswers(
                        parsed.horizonYears(), parsed.lossTolerance(), parsed.experience())));
                profile = scoring.score(answers);
            }
            return new TurnResult(parsed.assistantMessage(), profile);
        } catch (Exception e) {
            throw new IllegalArgumentException("The model did not return valid interview JSON", e);
        }
    }

    public record ExtractedTurn(String assistantMessage, Integer horizonYears, Integer lossTolerance, Integer experience) { }
    public record TurnResult(String assistantMessage, InvestorProfile profile) { }
}

package dev.knowledgeprofiling.app;

import dev.knowledgeprofiling.profiling.InterviewModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ChatClientInterviewModel implements InterviewModel {
    private static final String SCHEMA = "Return one JSON object only, with exactly these fields: " +
            "experience (BEGINNER|INTERMEDIATE|EXPERIENCED|null), knowledge (BASIC|MODERATE|ADVANCED|null), " +
            "objective (PRESERVATION|INCOME|GROWTH|null), horizonYears (integer 1..40 or null), " +
            "financialCapacity (LIMITED|MODERATE|COMFORTABLE|null), riskTolerance (integer 1..5 or null), " +
            "uncertainFields (array of criterion names from the schema), nextQuestion (string or null, max 300 chars). " +
            "Extract only information explicitly stated in this interview. Keep unconfirmed or ambiguous values null, " +
            "list ambiguous criterion names in uncertainFields, and ask one concise follow-up about the first missing criterion. " +
            "Do not request name, address, account details, exact income, or identifying information. " +
            "Never produce a score, category, recommendation, or investment advice.";

    private final ChatClient client;
    public ChatClientInterviewModel(ObjectProvider<ChatClient.Builder> builder) {
        ChatClient.Builder available = builder.getIfAvailable();
        this.client = available == null ? null : available.build();
    }

    @Override public String extractAsJson(String conversationHistory, String latestMessage, dev.knowledgeprofiling.profiling.RegulationKnowledgeBase.Citation regulationContext) {
        if (client == null) throw new InterviewUnavailableException();
        try {
            return client.prompt().system(SCHEMA)
                    .user("Historique de cette session synthétique :\n" + conversationHistory +
                            "\nDernier message utilisateur :\n" + latestMessage +
                            "\nContexte réglementaire public récupéré (utilise-le pour formuler la raison de la question, sans en déduire un score) :\n" + regulationContext.authority() +
                            " — " + regulationContext.excerpt() + "\nSource : " + regulationContext.url())
                    .call().content();
        } catch (RuntimeException e) { throw new InterviewUnavailableException(); }
    }
    public static final class InterviewUnavailableException extends RuntimeException { }
}

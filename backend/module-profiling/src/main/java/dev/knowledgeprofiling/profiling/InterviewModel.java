package dev.knowledgeprofiling.profiling;

public interface InterviewModel {
    String extractAsJson(String conversationHistory, String latestMessage, RegulationKnowledgeBase.Citation regulationContext);
}

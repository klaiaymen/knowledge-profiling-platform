package dev.knowledgeprofiling.rag;

import java.util.List;

public record RagAnswer(String answer, String confidence, List<RagSource> sources) { }

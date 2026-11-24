package dev.knowledgeprofiling.profiling;

import java.util.List;

public record ProfilingScore(int score, String category, List<CriterionScore> breakdown) {
    public ProfilingScore { breakdown = List.copyOf(breakdown); }
    public record CriterionScore(String criterion, int points, int maximum, String explanation) { }
}

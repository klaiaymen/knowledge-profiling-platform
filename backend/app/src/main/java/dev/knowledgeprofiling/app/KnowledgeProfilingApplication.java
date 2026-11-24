package dev.knowledgeprofiling.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "dev.knowledgeprofiling")
public class KnowledgeProfilingApplication {
    public static void main(String[] args) { SpringApplication.run(KnowledgeProfilingApplication.class, args); }
}

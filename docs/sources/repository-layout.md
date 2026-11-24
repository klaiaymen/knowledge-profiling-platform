# Organisation du dépôt

Le backend Java 21/Spring Boot est un parent Maven multi-modules : `core-rag` gère ingestion, recherche et citations ; `module-traceability` gère connecteurs et liens ; `module-profiling` gère entretien et score ; `app` assemble l'API REST. Le frontend utilise Angular 18, TypeScript strict, Angular Material et NgRx Signals. PostgreSQL avec pgvector et les migrations Flyway stockent les données indexées. Docker Compose fournit le service de base ; Ollama est optionnel via le profil `local-llm`.

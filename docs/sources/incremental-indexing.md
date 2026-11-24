# Indexation incrémentale

La traçabilité expose `POST /api/trace/reindex` pour indexer les connecteurs activés et `POST /api/trace/webhook` pour traiter les changements. Le contenu est comparé par empreinte SHA-256 afin de limiter le travail aux documents modifiés. Les résultats de recherche conservent les références de source nécessaires aux citations et à l'historique des fichiers.

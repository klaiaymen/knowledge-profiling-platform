# Réponses RAG et citations

Le noyau RAG recherche des passages dans les sources indexées et transmet ces passages au modèle. Une réponse inclut les citations récupérables et un indicateur de confiance. Lorsque les passages sont insuffisants, le service s'abstient, répond « information non trouvée dans les sources », retourne `LOW` et aucune source. Les citations doivent permettre de retrouver l'extrait exact et sa référence.

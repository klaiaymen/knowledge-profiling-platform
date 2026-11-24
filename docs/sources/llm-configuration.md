# Configuration des modèles

Spring AI choisit le fournisseur via les profils Spring. Ollama est le fournisseur local par défaut ; Anthropic est optionnel. Les clés et secrets de fournisseur viennent exclusivement de variables d'environnement et ne doivent pas être commis. Le modèle extrait les réponses et conduit l'entretien ; les règles Java déterministes restent seules responsables du score.

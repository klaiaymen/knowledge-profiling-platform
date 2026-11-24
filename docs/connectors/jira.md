# Connecteur Jira (désactivé par défaut)

Le connecteur Jira Cloud est conditionnel : il n'est enregistré que si `JIRA_ENABLED=true`. Il utilise l'API REST Jira v3 en lecture seule avec `JIRA_BASE_URL`, `JIRA_EMAIL`, `JIRA_API_TOKEN` et `JIRA_PROJECT_KEYS` (clés séparées par des virgules). Aucun identifiant ni jeton ne doit être ajouté au dépôt.

La première version récupère les tickets modifiés les plus récents, dans une limite de 100 résultats. Prévoir pagination, limites de débit, portée OAuth et tests de contrat avant toute utilisation réelle. Jira n'est pas nécessaire pour la démonstration locale.

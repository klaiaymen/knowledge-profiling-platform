# Connecteurs de traçabilité

`SourceConnector` est l'interface commune d'accès aux sources. `GitHubConnector` peut lire le code, les issues, les pull requests et les commits d'un dépôt public configurable via l'API REST GitHub ; le jeton est optionnel. `MarkdownConnector` lit un dossier local et simule des pages Confluence. Le connecteur Jira est désactivé par défaut et documenté. Bitbucket, Jira et Confluence réel restent des extensions futures.

Les références comme `#123` et `fixes #123`, ainsi que les chemins modifiés par une PR, alimentent les liens de traçabilité entre ticket, commit, PR, fichier et page documentaire.

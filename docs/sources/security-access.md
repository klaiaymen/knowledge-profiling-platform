# Contrôle d'accès

L'API utilise des jetons JWT et les rôles `USER` et `ADMIN`. Les utilisateurs authentifiés USER et ADMIN peuvent envoyer des requêtes RAG. L'upload de documents et la réindexation sont réservés à ADMIN. Les entrées sont validées, CORS est configuré pour le frontend et les journaux d'audit enregistrent utilisateur, action, date et ressource sans contenu sensible.

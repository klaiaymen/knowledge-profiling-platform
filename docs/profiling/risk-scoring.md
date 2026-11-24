# Grille de score ConviFin (prototype)

Cette grille est une règle pédagogique locale. Elle ne provient pas d'ESMA ou de l'AMF, ne recommande aucun produit et ne démontre pas la conformité à MiFID II. Le modèle n'obtient pas le champ `score` : il ne produit que des réponses structurées qui sont contrôlées, puis passées au service Java déterministe.

| Critère | Valeurs | Points |
| --- | --- | ---: |
| Expérience | débutant / intermédiaire / expérimenté | 0 / 8 / 15 (max 15) |
| Connaissances | basiques / modérées / avancées | 0 / 8 / 15 (max 15) |
| Objectif | préservation / revenus / croissance | 0 / 4 / 10 (max 10) |
| Horizon | 1–40 ans | `ceil(années × 20 / 40)` (max 20) |
| Capacité financière déclarée | limitée / modérée / confortable | 0 / 10 / 20 (max 20) |
| Tolérance au risque | 1–5 | `(niveau − 1) × 5` (max 20) |

Le score est la somme, plafonnée par construction à 100. Catégories : **Prudent** de 0 à 34, **Équilibré** de 35 à 69, **Dynamique** de 70 à 100. Une réponse manquante ou ambiguë empêche la génération du profil ; elle déclenche une question de suivi. Les limites acceptées sont 1–40 ans et 1–5 pour la tolérance.

## Exemples synthétiques

- minimum : débutant, connaissances basiques, préservation, 1 an, capacité limitée, tolérance 1 → 1 point, Prudent ;
- seuil de catégorie : score 35 commence Équilibré ; score 70 commence Dynamique ;
- maximum : expérimenté, connaissances avancées, croissance, 40 ans, capacité confortable, tolérance 5 → 100 points, Dynamique.

Les montants, revenus exacts et données d'identité ne font pas partie du questionnaire. Les catégories financières sont déclaratives et ne mesurent pas réellement une capacité à subir des pertes.

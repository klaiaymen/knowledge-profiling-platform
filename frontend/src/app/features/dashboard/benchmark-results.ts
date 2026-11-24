export const BENCHMARK_RESULTS = [
  { name: 'Score déterministe reproductible', passed: 5, total: 5, note: 'Cas synthétiques des règles et seuils' },
  { name: 'Réponse manquante ou ambiguë', passed: 1, total: 1, note: 'Le profil reste incomplet et demande une précision' },
  { name: 'Extraction JSON et limites', passed: 3, total: 3, note: 'Champs inconnus et valeurs hors limites sont rejetés' },
  { name: 'Isolation et suppression de session', passed: 1, total: 1, note: 'Seul le propriétaire pseudonymisé consulte ou supprime la session' }
] as const;

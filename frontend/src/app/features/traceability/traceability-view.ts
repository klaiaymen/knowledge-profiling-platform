import type { TraceItem } from '../../core/models';

export function orderedTimeline(items: readonly TraceItem[]): TraceItem[] {
  const rank: Record<string, number> = { 'github-issue': 0, 'github-pr': 1, 'github-commit': 2, markdown: 3, 'github-file': 4 };
  return [...items].sort((a, b) => (rank[a.sourceType] ?? 5) - (rank[b.sourceType] ?? 5) || a.title.localeCompare(b.title));
}

export function impactedPaths(items: readonly { path: string }[]): string[] {
  return [...new Set(items.map(item => item.path).filter(path => path.length > 0))].sort();
}

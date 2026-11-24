import type { RagAnswer } from '../../core/models';

export function confidenceClass(answer: RagAnswer): string {
  return answer.confidence === 'LOW' ? 'low' : answer.confidence === 'HIGH' ? 'high' : 'medium';
}

export function sourceReference(path: string, page: number | null, line: number | null): string {
  return [path, page === null ? null : `p.${page}`, line === null ? null : `ligne ${line}`].filter(Boolean).join(' · ');
}

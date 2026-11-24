export interface SseParseResult {
  events: string[];
  remainder: string;
}

export function takeSseEvents(buffer: string): SseParseResult {
  const normalized = buffer.replace(/\r\n/g, '\n');
  const blocks = normalized.split('\n\n');
  const remainder = blocks.pop() ?? '';
  const events = blocks.map(block => block.split('\n')
    .filter(line => line.startsWith('data:'))
    .map(line => line.slice(5).trimStart())
    .join('\n'))
    .filter(data => data.length > 0);
  return { events, remainder };
}

export function profileProgress(missingCriterion: string | null, complete: boolean): number {
  if (complete) return 100;
  const answered = missingCriterion ? ['experience', 'knowledge', 'objective', 'horizonYears', 'financialCapacity', 'riskTolerance'].indexOf(missingCriterion) : 6;
  return Math.max(0, Math.min(95, Math.round((Math.max(0, answered) / 6) * 100)));
}

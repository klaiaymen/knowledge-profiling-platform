import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class MetricsService {
  private startedAt = 0;
  readonly completedResponses = signal(0);
  readonly lowConfidenceResponses = signal(0);
  readonly totalResponseMs = signal(0);

  startRequest(): void { this.startedAt = performance.now(); }
  finishRequest(lowConfidence: boolean): number {
    const elapsed = Math.max(0, Math.round(performance.now() - this.startedAt));
    this.completedResponses.update(count => count + 1);
    this.lowConfidenceResponses.update(count => count + (lowConfidence ? 1 : 0));
    this.totalResponseMs.update(total => total + elapsed);
    return elapsed;
  }
  cancelRequest(): void { this.startedAt = 0; }
  averageResponseMs(): number { return this.completedResponses() === 0 ? 0 : Math.round(this.totalResponseMs() / this.completedResponses()); }
  lowConfidenceRate(): number { return this.completedResponses() === 0 ? 0 : Math.round(this.lowConfidenceResponses() / this.completedResponses() * 100); }
}

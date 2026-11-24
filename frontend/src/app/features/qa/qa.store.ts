import { Injectable, computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, finalize, pipe, switchMap, tap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { RagAnswer } from '../../core/models';
import { MetricsService } from '../shared/metrics.service';

const QaStoreBase = signalStore(
  withState({ query: '', answer: null as RagAnswer | null, loading: false, error: null as string | null, responseMs: null as number | null }),
  withComputed(store => ({ lowConfidence: computed(() => store.answer()?.confidence === 'LOW') })),
  withMethods((store, api = inject(ApiService), metrics = inject(MetricsService)) => ({
    ask: rxMethod<string>(pipe(
      tap(query => { patchState(store, { query, loading: true, error: null, answer: null }); metrics.startRequest(); }),
      switchMap(query => api.stream<RagAnswer>(`/api/rag/stream?question=${encodeURIComponent(query)}`).pipe(
        tap(answer => {
          const elapsed = metrics.finishRequest(answer.confidence === 'LOW');
          patchState(store, { answer, responseMs: elapsed });
        }),
        catchError((error: unknown) => { metrics.cancelRequest(); patchState(store, { error: error instanceof Error ? error.message : 'La recherche a échoué.' }); return EMPTY; }),
        finalize(() => patchState(store, { loading: false }))
      ))
    )),
    reset(): void { patchState(store, { query: '', answer: null, error: null, responseMs: null }); }
  }))
);

@Injectable({ providedIn: 'root' })
export class QaStore extends QaStoreBase {}

import { Injectable, computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, finalize, pipe, switchMap, tap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { FileHistory, ImpactResult } from '../../core/models';

const TraceabilityStoreBase = signalStore(
  withState({ path: '', history: null as FileHistory | null, impact: null as ImpactResult | null, loading: false, reindexing: false, error: null as string | null, notice: null as string | null }),
  withComputed(store => ({ hasHistory: computed(() => store.history() !== null), hasImpact: computed(() => store.impact() !== null), busy: computed(() => store.loading() || store.reindexing()) })),
  withMethods((store, api = inject(ApiService)) => ({
    searchFile: rxMethod<string>(pipe(
      tap(path => patchState(store, { path, loading: true, error: null, impact: null })),
      switchMap(path => api.get<FileHistory>(`/api/trace/file?path=${encodeURIComponent(path)}`).pipe(
        tap(history => patchState(store, { history })),
        catchError((error: unknown) => { patchState(store, { error: error instanceof Error ? error.message : 'Historique indisponible.' }); return EMPTY; }),
        finalize(() => patchState(store, { loading: false }))
      ))
    )),
    analyzeImpact: rxMethod<{ paths?: string[]; pullRequests?: number[] }>(pipe(
      tap(() => patchState(store, { loading: true, error: null })),
      switchMap(request => api.post<ImpactResult>('/api/trace/impact', request).pipe(
        tap(impact => patchState(store, { impact })),
        catchError((error: unknown) => { patchState(store, { error: error instanceof Error ? error.message : 'Analyse indisponible.' }); return EMPTY; }),
        finalize(() => patchState(store, { loading: false }))
      ))
    )),
    reindex: rxMethod<void>(pipe(
      tap(() => patchState(store, { reindexing: true, error: null, notice: null })),
      switchMap(() => api.post<{ changed: number; unchanged: number }>('/api/trace/reindex', {}).pipe(
        tap(result => patchState(store, { notice: `Indexation terminée : ${result.changed} modifiés, ${result.unchanged} inchangés.` })),
        catchError((error: unknown) => { patchState(store, { error: error instanceof Error ? error.message : 'Réindexation refusée ou indisponible.' }); return EMPTY; }),
        finalize(() => patchState(store, { reindexing: false }))
      ))
    )),
    clear(): void { patchState(store, { history: null, impact: null, error: null, notice: null }); }
  }))
);

@Injectable({ providedIn: 'root' })
export class TraceabilityStore extends TraceabilityStoreBase {}

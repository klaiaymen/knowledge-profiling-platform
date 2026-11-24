import { Injectable, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, finalize, pipe, switchMap, tap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ProfileReport, ProfileTurn } from '../../core/models';
import { profileProgress } from '../../core/sse';

interface ChatLine { speaker: 'assistant' | 'user'; text: string; citation?: string; why?: string; }
const ProfilingStoreBase = signalStore(
  withState({ sessionId: null as string | null, turns: [] as ChatLine[], missingCriterion: null as string | null, complete: false, profile: null as ProfileReport | null, loading: false, deleting: false, error: null as string | null }),
  withComputed(store => ({ progress: computed(() => profileProgress(store.missingCriterion(), store.complete())) })),
  withMethods((store, api = inject(ApiService), http = inject(HttpClient)) => ({
    createSession: rxMethod<void>(pipe(
      tap(() => patchState(store, { loading: true, error: null, sessionId: null, turns: [], profile: null, complete: false, missingCriterion: 'experience' })),
      switchMap(() => api.post<ProfileTurn>('/api/profiling/sessions', {}).pipe(
        tap(turn => patchState(store, { sessionId: turn.sessionId, missingCriterion: turn.missingCriterion, turns: [{ speaker: 'assistant', text: turn.assistantMessage, citation: turn.rationale?.url, why: turn.whyQuestionAsked }] })),
        catchError((error: unknown) => { patchState(store, { error: error instanceof Error ? error.message : 'Impossible de démarrer la session.' }); return EMPTY; }),
        finalize(() => patchState(store, { loading: false }))
      ))
    )),
    sendMessage: rxMethod<string>(pipe(
      tap(() => patchState(store, { loading: true, error: null })),
      switchMap(message => {
        const id = store.sessionId();
        if (!id) { patchState(store, { loading: false, error: 'Démarrez une session avant de répondre.' }); return EMPTY; }
        patchState(store, { turns: [...store.turns(), { speaker: 'user', text: message }] });
        return api.stream<ProfileTurn>(`/api/profiling/sessions/${encodeURIComponent(id)}/messages`, { message }).pipe(
          tap(turn => {
            const next: ChatLine = { speaker: 'assistant', text: turn.assistantMessage, citation: turn.rationale?.url, why: turn.whyQuestionAsked };
            patchState(store, { turns: [...store.turns(), next], missingCriterion: turn.missingCriterion, complete: turn.complete, profile: turn.profile });
          }),
          catchError((error: unknown) => { patchState(store, { error: error instanceof Error ? error.message : 'Réponse indisponible.' }); return EMPTY; }),
          finalize(() => patchState(store, { loading: false }))
        );
      })
    )),
    deleteSession: rxMethod<void>(pipe(
      tap(() => patchState(store, { deleting: true, error: null })),
      switchMap(() => {
        const id = store.sessionId();
        if (!id) { patchState(store, { deleting: false }); return EMPTY; }
        return api.delete(`/api/profiling/sessions/${encodeURIComponent(id)}`).pipe(
          tap(() => patchState(store, { sessionId: null, turns: [], profile: null, complete: false, missingCriterion: null })),
          catchError((error: unknown) => { patchState(store, { error: error instanceof Error ? error.message : 'Suppression impossible.' }); return EMPTY; }),
          finalize(() => patchState(store, { deleting: false }))
        );
      })
    )),
    downloadPdf(): void {
      const id = store.sessionId();
      if (!id || !store.complete()) return;
      http.get(`/api/profiling/sessions/${encodeURIComponent(id)}/profile.pdf`, { responseType: 'blob' }).subscribe({
        next: blob => { const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = 'convi-fin-profile.pdf'; link.click(); URL.revokeObjectURL(url); },
        error: () => patchState(store, { error: 'Export PDF indisponible.' })
      });
    },
    clearLocal(): void { patchState(store, { sessionId: null, turns: [], missingCriterion: null, complete: false, profile: null, loading: false, deleting: false, error: null }); }
  }))
);

@Injectable({ providedIn: 'root' })
export class ProfilingStore extends ProfilingStoreBase {}

import { Injectable, computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, finalize, pipe, switchMap, tap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ProfileDashboard } from '../../core/models';
import { MetricsService } from '../shared/metrics.service';
import { BENCHMARK_RESULTS } from './benchmark-results';

const DashboardStoreBase = signalStore(
  withState({ profiles: null as ProfileDashboard | null, loading: false, error: null as string | null, benchmarks: BENCHMARK_RESULTS }),
  withComputed(store => ({ totalProfiles: computed(() => store.profiles()?.completedProfiles ?? 0), benchmarkPassRate: computed(() => {
    const entries = store.benchmarks();
    const total = entries.reduce((sum, item) => sum + item.total, 0);
    return total === 0 ? 0 : Math.round(entries.reduce((sum, item) => sum + item.passed, 0) / total * 100);
  }) })),
  withMethods((store, api = inject(ApiService), metrics = inject(MetricsService)) => ({
    load: rxMethod<void>(pipe(
      tap(() => patchState(store, { loading: true, error: null })),
      switchMap(() => api.get<ProfileDashboard>('/api/profiling/dashboard').pipe(
        tap(profiles => patchState(store, { profiles })),
        catchError((error: unknown) => { patchState(store, { error: error instanceof Error ? error.message : 'Dashboard indisponible.' }); return EMPTY; }),
        finalize(() => patchState(store, { loading: false }))
      ))
    )),
    averageResponseMs: (): number => metrics.averageResponseMs(),
    lowConfidenceRate: (): number => metrics.lowConfidenceRate()
  }))
);

@Injectable({ providedIn: 'root' })
export class DashboardStore extends DashboardStoreBase {}

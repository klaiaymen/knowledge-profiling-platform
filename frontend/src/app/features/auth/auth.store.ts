import { Injectable, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, pipe, switchMap, tap } from 'rxjs';
import { Role, allowsRole, rolesFromJwt } from '../../core/access';
import { TokenResponse } from '../../core/models';

interface AuthState { accessToken: string | null; refreshToken: string | null; roles: Role[]; user: string | null; loading: boolean; error: string | null; }
const initialState = (): AuthState => {
  const accessToken = typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('kpf-token');
  let user: string | null = null;
  try { user = accessToken ? JSON.parse(atob(accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).sub as string : null; } catch { user = null; }
  return { accessToken, refreshToken: typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem('kpf-refresh-token'), roles: rolesFromJwt(accessToken), user, loading: false, error: null };
};

const AuthStoreBase = signalStore(
  withState(initialState),
  withComputed(store => ({ isAuthenticated: computed(() => store.accessToken() !== null), isAdmin: computed(() => allowsRole(store.roles(), 'ROLE_ADMIN')) })),
  withMethods((store, http = inject(HttpClient)) => ({
    login: rxMethod<{ username: string; password: string }>(pipe(
      tap(() => patchState(store, { loading: true, error: null })),
      switchMap(credentials => http.post<TokenResponse>('/api/auth/login', credentials).pipe(
        tap(tokens => {
          sessionStorage.setItem('kpf-token', tokens.accessToken);
          sessionStorage.setItem('kpf-refresh-token', tokens.refreshToken);
          const payload = JSON.parse(atob(tokens.accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as { sub?: string };
          patchState(store, { accessToken: tokens.accessToken, refreshToken: tokens.refreshToken, roles: rolesFromJwt(tokens.accessToken), user: payload.sub ?? credentials.username, loading: false, error: null });
        }),
        catchError(() => { patchState(store, { loading: false, error: 'Connexion impossible. Vérifiez les identifiants synthétiques et le backend.' }); return EMPTY; })
      ))
    )),
    logout(): void {
      sessionStorage.removeItem('kpf-token'); sessionStorage.removeItem('kpf-refresh-token');
      patchState(store, { accessToken: null, refreshToken: null, roles: [], user: null, loading: false, error: null });
    },
    canAccess(role: Role): boolean { return allowsRole(store.roles(), role); },
    clearError(): void { patchState(store, { error: null }); }
  }))
);

@Injectable({ providedIn: 'root' })
export class AuthStore extends AuthStoreBase {}

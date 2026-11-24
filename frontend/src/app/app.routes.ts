import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards';
import { LoginPage } from './features/auth/login.page';
import { QaPage } from './features/qa/qa.page';
import { TraceabilityPage } from './features/traceability/traceability.page';
import { ReindexPage } from './features/traceability/reindex.page';
import { ProfilingPage } from './features/profiling/profiling.page';
import { DashboardPage } from './features/dashboard/dashboard.page';

export const APP_ROUTES: Routes = [
  { path: 'login', component: LoginPage },
  { path: 'qa', component: QaPage, canActivate: [authGuard] },
  { path: 'traceability', component: TraceabilityPage, canActivate: [authGuard] },
  { path: 'traceability/reindex', component: ReindexPage, canActivate: [roleGuard('ROLE_ADMIN')] },
  { path: 'profiling', component: ProfilingPage, canActivate: [authGuard] },
  { path: 'dashboard', component: DashboardPage, canActivate: [authGuard] },
  { path: '', pathMatch: 'full', redirectTo: 'qa' },
  { path: '**', redirectTo: 'qa' }
];

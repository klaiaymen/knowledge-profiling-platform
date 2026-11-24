import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { AuthStore } from './features/auth/auth.store';
import { ProfilingStore } from './features/profiling/profiling.store';
import { QaStore } from './features/qa/qa.store';
import { TraceabilityStore } from './features/traceability/traceability.store';

interface Health { status: string; }

@Component({
  selector: 'app-root', standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, MatToolbarModule],
  template: `<a class="skip-link" href="#main-content">Passer au contenu principal</a>
    <mat-toolbar class="topbar"><a class="brand" routerLink="/qa" aria-label="Accueil Knowledge Profiling Platform">KPP</a>
      @if (auth.isAuthenticated()) {
        <nav aria-label="Navigation principale" class="nav-links">
          <a mat-button routerLink="/qa" routerLinkActive="active" ariaCurrentWhenActive="page">Questions-réponses</a>
          <a mat-button routerLink="/traceability" routerLinkActive="active" ariaCurrentWhenActive="page">Traçabilité</a>
          <a mat-button routerLink="/profiling" routerLinkActive="active" ariaCurrentWhenActive="page">Profilage</a>
          <a mat-button routerLink="/dashboard" routerLinkActive="active" ariaCurrentWhenActive="page">Dashboard</a>
        </nav>
        <span class="toolbar-spacer"></span><span class="user-badge">{{ auth.user() }} · {{ auth.isAdmin() ? 'ADMIN' : 'USER' }}</span>
        <button mat-button (click)="logout()" aria-label="Fermer la session">Déconnexion</button>
      } @else { <span class="toolbar-spacer"></span><a mat-button routerLink="/login">Connexion</a> }
    </mat-toolbar>
    <div class="health-strip" role="status">API : {{ healthStatus() }}</div>
    <div id="main-content"><router-outlet /></div>
    <footer><p>Prototype de portfolio · données synthétiques uniquement · aucun conseil financier</p></footer>`
})
export class AppComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  readonly auth = inject(AuthStore);
  private readonly profiling = inject(ProfilingStore);
  private readonly qa = inject(QaStore);
  private readonly traceability = inject(TraceabilityStore);
  readonly healthStatus = signal('vérification…');

  ngOnInit(): void {
    this.http.get<Health>('/api/health').subscribe({
      next: health => this.healthStatus.set(health.status),
      error: () => this.healthStatus.set('indisponible')
    });
  }
  logout(): void {
    if (this.profiling.sessionId()) this.profiling.deleteSession();
    this.profiling.clearLocal(); this.qa.reset(); this.traceability.clear();
    this.auth.logout(); void this.router.navigateByUrl('/login');
  }
}

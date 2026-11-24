import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { DashboardStore } from './dashboard.store';

@Component({
  selector: 'app-dashboard-page', standalone: true, imports: [MatButtonModule, MatCardModule],
  template: `<main class="page"><header><p class="eyebrow">Mesures de démonstration</p><h1>Dashboard</h1><p>Les agrégats de profil sont anonymisés. Les métriques de latence sont locales à cet onglet.</p></header>
    <button mat-stroked-button (click)="store.load()" [disabled]="store.loading()">Actualiser</button>
    @if (store.loading()) { <p role="status">Chargement des agrégats anonymisés…</p> }
    @if (store.error()) { <p class="error" role="alert">{{ store.error() }}</p> }
    @if (store.profiles(); as data) { <section class="metric-grid" aria-label="Agrégats anonymisés">
      <mat-card><mat-card-content><h2>{{ data.completedProfiles }}</h2><p>Profils terminés (sessions conservées en mémoire)</p></mat-card-content></mat-card>
      <mat-card><mat-card-content><h2>{{ data.prudent }}</h2><p>Prudent</p></mat-card-content></mat-card>
      <mat-card><mat-card-content><h2>{{ data.balanced }}</h2><p>Équilibré</p></mat-card-content></mat-card>
      <mat-card><mat-card-content><h2>{{ data.dynamic }}</h2><p>Dynamique</p></mat-card-content></mat-card>
      <mat-card><mat-card-content><h2>{{ store.averageResponseMs() }} ms</h2><p>Temps moyen de réponse Q&amp;A (onglet courant)</p></mat-card-content></mat-card>
      <mat-card><mat-card-content><h2>{{ store.lowConfidenceRate() }} %</h2><p>Réponses à faible confiance Q&amp;A (onglet courant)</p></mat-card-content></mat-card>
    </section> }
    <section><h2>Benchmarks synthétiques</h2><p>{{ store.benchmarkPassRate() }} % de scénarios fixture réussis. Mesure pédagogique associée aux tests automatisés, pas une mesure de qualité en production.</p><div class="metric-grid">@for (benchmark of store.benchmarks(); track benchmark.name) { <mat-card><mat-card-content><h3>{{ benchmark.name }}</h3><p>{{ benchmark.passed }}/{{ benchmark.total }} scénarios réussis</p><small>{{ benchmark.note }}</small></mat-card-content></mat-card> }</div></section>
  </main>`
})
export class DashboardPage implements OnInit {
  readonly store = inject(DashboardStore);
  ngOnInit(): void { this.store.load(); }
}

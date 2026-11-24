import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthStore } from '../auth/auth.store';
import { TraceabilityStore } from './traceability.store';
import { orderedTimeline } from './traceability-view';

@Component({
  selector: 'app-traceability-page', standalone: true,
  imports: [FormsModule, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  template: `<main class="page"><header><p class="eyebrow">Code · tickets · changements · documentation</p><h1>Traçabilité</h1><p>Remontez les liens et consultez les justifications indexées.</p></header>
    <mat-card><mat-card-content><form (ngSubmit)="search()"><mat-form-field appearance="outline"><mat-label>Chemin du fichier</mat-label><input matInput name="path" [(ngModel)]="path" placeholder="src/Rule.java" required></mat-form-field><button mat-flat-button color="primary" [disabled]="store.loading() || !path.trim()">Rechercher l'historique</button></form>
      @if (auth.isAdmin()) { <a mat-stroked-button routerLink="/traceability/reindex">Réindexer les sources (ADMIN)</a> }
      @if (store.error()) { <p class="error" role="alert">{{ store.error() }}</p> }
      @if (store.history(); as history) { <section><h2>Frise : {{ history.path }}</h2><ol class="timeline">
        @for (item of timeline(history.related); track item.externalId) { <li><span class="timeline-dot"></span><article><p class="eyebrow">{{ item.sourceType }} · {{ item.relationship }}</p><h3>{{ item.title }}</h3><p>{{ item.justification }}</p><blockquote>{{ item.excerpt }}</blockquote><small>{{ item.externalId }}</small>@if (item.url) { <p><a [href]="item.url" target="_blank" rel="noopener noreferrer">Ouvrir la source exacte</a></p> }</article></li> }
      </ol>@if (!history.related.length) { <p>Aucun lien indexé pour ce fichier.</p> }</section> }
    </mat-card-content></mat-card>
    <mat-card><mat-card-content><h2>Analyse d'impact d'une PR</h2><form (ngSubmit)="impact()"><mat-form-field appearance="outline"><mat-label>Numéro de pull request</mat-label><input matInput name="prNumber" type="number" min="1" [(ngModel)]="prNumber" required></mat-form-field><button mat-stroked-button [disabled]="store.loading() || prNumber < 1">Analyser l'impact</button></form>
      @if (store.impact(); as result) { <ul>@for (item of result.impacted; track item.source.externalId) { <li><strong>{{ item.path || item.source.path }}</strong> — {{ item.justification }} <a [href]="item.source.url" target="_blank" rel="noopener noreferrer">{{ item.source.title }}</a></li> }</ul>@if (!result.impacted.length) { <p>Aucun fichier lié à cette PR dans l'index.</p> } }
    </mat-card-content></mat-card>
  </main>`
})
export class TraceabilityPage {
  readonly store = inject(TraceabilityStore);
  readonly auth = inject(AuthStore);
  path = '';
  prNumber = 1;
  readonly timeline = orderedTimeline;
  search(): void { if (this.path.trim()) this.store.searchFile(this.path.trim()); }
  impact(): void { if (this.prNumber > 0) this.store.analyzeImpact({ pullRequests: [Number(this.prNumber)] }); }
}

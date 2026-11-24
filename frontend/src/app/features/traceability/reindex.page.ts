import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { TraceabilityStore } from './traceability.store';

@Component({
  selector: 'app-reindex-page', standalone: true, imports: [MatButtonModule, MatCardModule],
  template: `<main class="narrow-page"><mat-card><p class="eyebrow">Administration</p><h1>Réindexer les sources</h1><p>Cette opération récupère les sources configurées et ne recalcule que les documents modifiés.</p><button mat-flat-button color="primary" (click)="store.reindex()" [disabled]="store.reindexing()">{{ store.reindexing() ? 'Indexation…' : 'Lancer la réindexation' }}</button>@if (store.notice()) { <p role="status">{{ store.notice() }}</p> }@if (store.error()) { <p class="error" role="alert">{{ store.error() }}</p> }</mat-card></main>`
})
export class ReindexPage { readonly store = inject(TraceabilityStore); }

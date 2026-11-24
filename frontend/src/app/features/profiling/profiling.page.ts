import { Component, ElementRef, ViewChild, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { JsonPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ProfilingStore } from './profiling.store';

@Component({
  selector: 'app-profiling-page', standalone: true,
  imports: [FormsModule, JsonPipe, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatProgressBarModule],
  template: `<main class="page"><header><p class="eyebrow">ConviFin · prototype pédagogique</p><h1>Profilage adaptatif</h1><p>Les réponses ne sont gardées qu'en mémoire pour cette session. Utilisez uniquement des réponses synthétiques.</p></header>
    <mat-card><mat-card-content>
      @if (!store.sessionId()) { <button mat-flat-button color="primary" (click)="store.createSession()" [disabled]="store.loading()">Démarrer l'entretien</button> }
      @else { <div class="progress-label"><span>Progression</span><span>{{ store.progress() }} %</span></div><mat-progress-bar mode="determinate" [value]="store.progress()" aria-label="Progression de l'entretien"></mat-progress-bar>
        <section class="chat" #chat aria-label="Conversation de profilage" aria-live="polite">
          @for (line of store.turns(); track $index) { <article class="chat-line" [class.user-line]="line.speaker === 'user'"><strong>{{ line.speaker === 'user' ? 'Vous' : 'Assistant' }}</strong><p>{{ line.text }}</p>@if (line.why) { <small>{{ line.why }}</small> }@if (line.citation) { <p><a [href]="line.citation" target="_blank" rel="noopener noreferrer">Source réglementaire citée</a></p> }</article> }
          @if (store.loading()) { <p role="status">Analyse de la réponse et préparation du suivi…</p> }
        </section>
        @if (!store.complete()) { <form (ngSubmit)="send()"><mat-form-field appearance="outline"><mat-label>Votre réponse synthétique</mat-label><textarea matInput name="message" [(ngModel)]="message" maxlength="2000" rows="2" required></textarea><mat-hint>Pas de nom, coordonnées, montant de revenu ou identifiant réel.</mat-hint></mat-form-field><button mat-flat-button color="primary" [disabled]="store.loading() || !message.trim()">Envoyer la réponse</button></form> }
        @if (store.profile(); as profile) { <section class="profile-result" aria-live="polite"><h2>Profil pédagogique</h2><p class="score">{{ profile.score }} / 100 · {{ profile.category }}</p><p>{{ profile.justification }}</p><h3>Règles et pondérations</h3><ul>@for (rule of profile.breakdown; track rule.criterion) { <li>{{ rule.criterion }} : {{ rule.points }}/{{ rule.maximum }} — {{ rule.explanation }}</li> }</ul><h3>Réponses structurées</h3><pre>{{ profile.answers | json }}</pre><p><strong>Limites :</strong> {{ profile.limitations }}</p><p class="notice">{{ profile.warning }}</p><h3>Sources</h3><ul>@for (citation of profile.citations; track citation.url) { <li><a [href]="citation.url" target="_blank" rel="noopener noreferrer">{{ citation.authority }}</a> — {{ citation.excerpt }}</li> }</ul><button mat-stroked-button (click)="store.downloadPdf()">Exporter en PDF</button></section> }
        <div class="button-row"><button mat-button color="warn" (click)="store.deleteSession()" [disabled]="store.deleting()">Supprimer cette session</button></div>
      }
      @if (store.error()) { <p class="error" role="alert">{{ store.error() }}</p> }
      <p class="notice">Exercice basé sur des données synthétiques. Ce résultat n'est ni un conseil financier ni une validation de conformité MiFID II.</p>
    </mat-card-content></mat-card>
  </main>`
})
export class ProfilingPage {
  readonly store = inject(ProfilingStore);
  @ViewChild('chat') private chat?: ElementRef<HTMLElement>;
  message = '';
  constructor() { effect(() => { this.store.turns().length; queueMicrotask(() => this.chat?.nativeElement.scrollTo({ top: this.chat.nativeElement.scrollHeight, behavior: 'smooth' })); }); }
  send(): void { const value = this.message.trim(); if (value) { this.store.sendMessage(value); this.message = ''; } }
}

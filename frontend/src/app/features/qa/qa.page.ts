import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { QaStore } from './qa.store';
import { confidenceClass, sourceReference } from './qa-view';

@Component({
  selector: 'app-qa-page', standalone: true,
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  template: `<main class="page"><header><p class="eyebrow">Connaissances techniques</p><h1>Questions-réponses</h1><p>Posez une question et vérifiez chaque réponse dans ses sources.</p></header>
    <mat-card><mat-card-content>
      <form (ngSubmit)="ask()"><mat-form-field appearance="outline"><mat-label>Votre question</mat-label><textarea matInput name="query" [(ngModel)]="query" rows="3" maxlength="2000" required aria-describedby="qa-hint"></textarea><mat-hint id="qa-hint">Réponse basée sur les documents indexés.</mat-hint></mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="store.loading() || !query.trim()">{{ store.loading() ? 'Recherche…' : 'Poser la question' }}</button>
      </form>
      @if (store.error()) { <p class="error" role="alert">{{ store.error() }}</p> }
      @if (store.answer(); as result) {
        <section aria-live="polite" class="answer"><div class="answer-heading"><h2>Réponse</h2><span class="confidence" [class]="'confidence ' + confidenceClass(result)">Confiance {{ result.confidence }}</span></div>
          <p>{{ result.answer }}</p><h3>Sources</h3>
          @for (source of result.sources; track source.citation) { <article class="source-card" [id]="'source-' + source.citation"><h4>{{ source.citation }} · {{ source.title }}</h4>
            <p><code>{{ sourceReference(source.path, source.page, source.line) }}</code></p>
            <details><summary>Afficher l'extrait exact</summary><blockquote>{{ source.excerpt }}</blockquote></details>
            @if (source.url) { <a [href]="source.url" target="_blank" rel="noopener noreferrer">Ouvrir la source exacte</a> } @else { <a [href]="'#source-' + source.citation">Aller à l'extrait exact</a> }
          </article> }
          @if (!result.sources.length) { <p>Aucun extrait n'a été retenu.</p> }
        </section>
      }
    </mat-card-content></mat-card>
  </main>`
})
export class QaPage {
  readonly store = inject(QaStore);
  query = '';
  readonly confidenceClass = confidenceClass;
  readonly sourceReference = sourceReference;
  ask(): void { if (this.query.trim()) this.store.ask(this.query.trim()); }
}

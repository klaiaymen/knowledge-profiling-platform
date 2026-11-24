import { Component, effect, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthStore } from './auth.store';

@Component({
  selector: 'app-login-page', standalone: true,
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  template: `<main class="narrow-page"><mat-card>
    <p class="eyebrow">Accès démo</p><h1>Connexion</h1>
    <p>Utilisez un compte de démonstration et des données synthétiques.</p>
    <form (ngSubmit)="submit()">
      <mat-form-field appearance="outline"><mat-label>Identifiant de démonstration</mat-label><input matInput name="username" [(ngModel)]="username" autocomplete="username" required></mat-form-field>
      <mat-form-field appearance="outline"><mat-label>Mot de passe</mat-label><input matInput name="password" [(ngModel)]="password" type="password" autocomplete="current-password" required></mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="auth.loading() || !username || !password">{{ auth.loading() ? 'Connexion…' : 'Se connecter' }}</button>
    </form>
    @if (auth.error()) { <p class="error" role="alert">{{ auth.error() }}</p> }
  </mat-card></main>`
})
export class LoginPage {
  readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  username = '';
  password = '';
  constructor() { effect(() => { if (this.auth.isAuthenticated()) void this.router.navigateByUrl('/qa'); }); }
  submit(): void { this.auth.login({ username: this.username.trim(), password: this.password }); this.password = ''; }
}

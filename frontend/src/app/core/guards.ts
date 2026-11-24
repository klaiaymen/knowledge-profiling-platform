import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore } from '../features/auth/auth.store';
import { Role } from './access';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  return auth.isAuthenticated() ? true : inject(Router).parseUrl('/login');
};

export function roleGuard(required: Role): CanActivateFn {
  return () => {
    const auth = inject(AuthStore);
    return !auth.isAuthenticated() ? inject(Router).parseUrl('/login') :
      auth.canAccess(required) ? true : inject(Router).parseUrl('/qa');
  };
}

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { isTokenExpired } from './jwt.util';
import { TokenStore } from './token.store';

/**
 * Keeps every screen except `/login` behind a valid, unexpired **administrator** token.
 *
 * <p>The role check is the point. Requiring only a valid token was not enough: an invitation
 * code lets somebody create a `ROLE_STUDENT` account for the mobile app, and that account's
 * token opened this portal and showed the whole navigation — Users, Invitation codes, Visitor
 * passes. The API refused every one of those calls with a `403`, so nothing leaked, but a
 * console full of screens that answer "forbidden" is not a boundary, it is a boundary that
 * happens to hold.
 *
 * <p>This is the client's own check and it is not a security control — anyone can edit their
 * own browser. The control is the `@PreAuthorize` on each endpoint, which is asserted per role
 * in the backend's tests. This stops the portal from *showing* an administration console to
 * somebody who is not an administrator.
 *
 * <p><strong>To sign in as a student while testing</strong> — which is a real thing to want,
 * since the API console and the role inspector exist for exactly that — remove `ROLE_ADMIN`
 * from the check below. It is deliberately one line.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const tokenStore = inject(TokenStore);
  const router = inject(Router);

  if (!tokenStore.isAuthenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }

  // A token past its deadline is a session that ended while the tab was closed or asleep; the
  // sign-in page says so rather than just asking for a password again. Read against the clock
  // now, not through `tokenStore.isExpired`: that is computed once per token and never
  // notices the deadline pass.
  const claims = tokenStore.decoded()?.claims;
  if (!claims || isTokenExpired(claims)) {
    tokenStore.clear();
    return router.createUrlTree(['/login'], { queryParams: { reason: 'expired', returnUrl: state.url } });
  }

  if (!tokenStore.roles().includes('ROLE_ADMIN')) {
    // `reason` rather than a silent bounce: landing back on the sign-in page having just
    // signed in successfully reads as a broken login, not as a refusal.
    return router.createUrlTree(['/login'], { queryParams: { reason: 'admin-only' } });
  }

  return true;
};

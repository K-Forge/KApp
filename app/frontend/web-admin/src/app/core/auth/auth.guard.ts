import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { AuthService } from './auth.service';
import { isTokenExpired } from './jwt.util';
import { opensPortal } from './permissions';
import { TokenStore } from './token.store';

/**
 * Keeps every screen except `/login` behind a valid, unexpired token that holds a **permission** -
 * administration, reception, maintenance, moderation or Bienestar - renewing an expired one first
 * when the session still can be. Which sections each permission sees is `sectionGuard`'s.
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
 * in the backend's tests. This stops the portal from *showing* its screens to somebody whose
 * account the server would refuse here anyway: `POST /auth/microsoft` for the portal asks for the
 * same permissions.
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
    const expired = () => {
      tokenStore.clear();
      return router.createUrlTree(['/login'], { queryParams: { reason: 'expired', returnUrl: state.url } });
    };
    // The refresh token outlives the access token: a tab reopened after lunch renews the
    // session instead of asking to sign in again.
    if (!claims || !tokenStore.refreshToken()) {
      return expired();
    }
    return inject(AuthService)
      .renew()
      .then((renewed) => (renewed ? admitted(tokenStore, router) : expired()));
  }

  return admitted(tokenStore, router);
};

function admitted(tokenStore: TokenStore, router: Router): true | UrlTree {
  if (!opensPortal(tokenStore.roles())) {
    // `reason` rather than a silent bounce: landing back on the sign-in page having just
    // signed in successfully reads as a broken login, not as a refusal.
    return router.createUrlTree(['/login'], { queryParams: { reason: 'no-permission' } });
  }
  return true;
}

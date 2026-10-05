import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { isTokenExpired } from './jwt.util';
import { TokenStore } from './token.store';

/**
 * The endpoints that establish an identity rather than use one.
 *
 * <p>None of them takes a bearer token, and sending a stale one turns a perfectly good request
 * into a 401 before the handler ever runs. On the login form that was very visible: with an
 * expired token still in localStorage, the first sign-in attempt failed with "Authentication
 * required" - your correct password, rejected, blamed on you - and only the second worked,
 * because the 401 below had cleared the token by then. Tokens last an hour, so everybody met
 * this every day.
 */
const UNAUTHENTICATED_PATHS = [
  '/auth/login',
  '/auth/register',
  '/auth/verify',
  '/auth/visitor-passes/',
  '/.well-known/jwks.json',
];

function establishesIdentity(url: string): boolean {
  return UNAUTHENTICATED_PATHS.some((path) => url.includes(path));
}

/**
 * Attaches the bearer token to every outgoing request that takes one, and reacts to a 401 the
 * same way everywhere: clear the session and return to login instead of leaving the UI in a
 * half-signed-in state showing stale data behind failed requests. The error still propagates
 * after that, so the screen that made the call (the login form included) can show its own
 * message.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStore = inject(TokenStore);
  const router = inject(Router);

  const token = tokenStore.raw();
  const authedReq =
    token && !establishesIdentity(req.url)
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(authedReq).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401) {
        const claims = tokenStore.decoded()?.claims;
        const expired = !!claims && isTokenExpired(claims);
        tokenStore.clear();
        if (!router.url.startsWith('/login')) {
          router.navigateByUrl(expired ? '/login?reason=expired' : '/login');
        }
      }
      return throwError(() => err);
    }),
  );
};

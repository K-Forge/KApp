import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { TokenStore } from './token.store';

/** A token decodeJwt reads; nothing here checks its signature. */
function token(expiresAtMs: number, roles = ['ROLE_STAFF', 'ROLE_ADMIN']): string {
  const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/=+$/, '');
  return `${encode({ alg: 'RS256' })}.${encode({ sub: 'x', roles, exp: Math.floor(expiresAtMs / 1000) })}.signature`;
}

describe('authGuard', () => {
  let store: TokenStore;
  const renew = vi.fn<() => Promise<boolean>>();

  const run = () =>
    TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/data/users' } as RouterStateSnapshot),
    );
  const loginReason = (result: unknown) =>
    result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : result;

  beforeEach(() => {
    renew.mockReset();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideRouter([]), { provide: AuthService, useValue: { renew } }],
    });
    store = TestBed.inject(TokenStore);
    store.clear();
  });

  it('lets an administrator with a live token in', () => {
    store.set(token(Date.now() + 60_000));

    expect(run()).toBe(true);
  });

  // Any permission opens the portal, as POST /auth/microsoft decides for the PORTAL client.
  it('lets reception in, with no administrator in sight', () => {
    store.set(token(Date.now() + 60_000, ['ROLE_STAFF', 'ROLE_RECEPTION']));

    expect(run()).toBe(true);
  });

  it('sends a profile role with no permission back to sign in, saying why', () => {
    store.set(token(Date.now() + 60_000, ['ROLE_STAFF']));

    expect(loginReason(run())).toBe('/login?reason=no-permission');
  });

  // A tab reopened after lunch: the access token is gone, the session is not.
  it('renews an expired token when the session still can be', async () => {
    store.start({ accessToken: token(Date.now() - 1_000), refreshToken: 'rt_1' });
    renew.mockImplementation(async () => {
      store.start({ accessToken: token(Date.now() + 3_600_000), refreshToken: 'rt_2' });
      return true;
    });

    expect(await run()).toBe(true);
  });

  it('sends an expired session to sign in again when it cannot be renewed', async () => {
    store.start({ accessToken: token(Date.now() - 1_000), refreshToken: 'rt_1' });
    renew.mockResolvedValue(false);

    expect(loginReason(await run())).toBe('/login?reason=expired&returnUrl=%2Fdata%2Fusers');
    expect(store.raw()).toBeNull();
  });

  it('asks nothing of the server for an expired token with no refresh token', () => {
    store.set(token(Date.now() - 1_000));

    expect(loginReason(run())).toBe('/login?reason=expired&returnUrl=%2Fdata%2Fusers');
    expect(renew).not.toHaveBeenCalled();
  });
});

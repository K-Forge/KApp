import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { TokenStore } from './token.store';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let tokenStore: TokenStore;
  let router: { url: string; navigateByUrl: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    // Some CI/runtime combinations don't expose a working localStorage in the test environment;
    // TokenStore already tolerates that in the app itself, so the test does the same rather than
    // depending on a browser API it isn't actually exercising here.
    try {
      localStorage.clear();
    } catch {
      /* not available in this test environment */
    }
    router = { url: '/api-console', navigateByUrl: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    tokenStore = TestBed.inject(TokenStore);
  });

  it('attaches the bearer token from TokenStore to outgoing requests', () => {
    tokenStore.set('a.b.c');

    http.get('/api/users').subscribe();

    const req = httpMock.expectOne('/api/users');
    expect(req.request.headers.get('Authorization')).toBe('Bearer a.b.c');
    req.flush({});
  });

  // A stale token on /auth/login is rejected before the handler runs, so a correct password
  // came back as "Authentication required" and only the second attempt worked - the 401 below
  // having cleared the token in between. Tokens last an hour; everybody met this daily.
  it('never sends a token to the endpoints that establish an identity', () => {
    tokenStore.set('stale.expired.token');

    for (const url of [
      '/auth/login',
      '/auth/sign-in-options',
      '/auth/microsoft',
      '/auth/refresh',
      '/auth/logout',
      '/auth/register',
      '/auth/verify',
      '/auth/visitor-passes/KL-V-ABCD-1234/redeem',
      '/.well-known/jwks.json',
    ]) {
      http.post(url, {}).subscribe({ error: () => undefined });
      const req = httpMock.expectOne(url);
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    }
  });

  it('still sends it to everything else, including other auth endpoints', () => {
    tokenStore.set('a.b.c');

    http.get('/auth/admin/invitation-codes').subscribe();

    const req = httpMock.expectOne('/auth/admin/invitation-codes');
    expect(req.request.headers.get('Authorization')).toBe('Bearer a.b.c');
    req.flush([]);
  });

  it('sends no Authorization header when there is no token', () => {
    http.get('/api/map/buildings').subscribe();

    const req = httpMock.expectOne('/api/map/buildings');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('clears the session and redirects to login on a 401', () => {
    tokenStore.set('a.b.c');
    let caughtStatus: number | undefined;

    http.get('/api/users').subscribe({
      error: (err) => (caughtStatus = err.status),
    });

    httpMock.expectOne('/api/users').flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    expect(tokenStore.raw()).toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
    // The error still reaches the caller so the screen that made the call can react too.
    expect(caughtStatus).toBe(401);
  });

  it('does not redirect again when already on the login screen', () => {
    router.url = '/login';
    tokenStore.set('a.b.c');

    http.get('/api/users').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/users').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('leaves the session untouched on errors other than 401', () => {
    tokenStore.set('a.b.c');

    http.get('/api/users').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/users').flush({}, { status: 500, statusText: 'Server Error' });

    expect(tokenStore.raw()).toBe('a.b.c');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  afterEach(() => {
    httpMock.verify();
  });
});

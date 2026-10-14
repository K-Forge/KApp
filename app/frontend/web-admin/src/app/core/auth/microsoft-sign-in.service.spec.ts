import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiConfigService } from '../config/api-config.service';
import { challengeOf, MicrosoftSignInService, nonceOf } from './microsoft-sign-in.service';
import { TokenStore } from './token.store';

/** An unsigned token whose payload is `claims`: only the nonce is read on this side. */
function idToken(claims: object): string {
  const part = (value: object) => btoa(JSON.stringify(value)).replace(/=+$/, '');
  return `${part({ alg: 'RS256' })}.${part(claims)}.signature`;
}

const PENDING = {
  state: 'state-1',
  nonce: 'nonce-1',
  verifier: 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk',
  tokenEndpoint: 'http://localhost:8090/kapp/token',
  clientId: 'kapp-portal-dev',
  redirectUri: 'http://localhost:4300/auth/callback',
  returnUrl: '/data/users',
};

describe('MicrosoftSignInService', () => {
  let service: MicrosoftSignInService;
  let http: HttpTestingController;
  const start = vi.fn();

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ApiConfigService, useValue: { baseUrl: () => 'http://localhost:8080' } },
        { provide: TokenStore, useValue: { start } },
      ],
    });
    service = TestBed.inject(MicrosoftSignInService);
    http = TestBed.inject(HttpTestingController);
    sessionStorage.setItem('kapp-admin:microsoft-sign-in', JSON.stringify(PENDING));
    start.mockClear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    sessionStorage.clear();
  });

  it('derives the PKCE challenge as RFC 7636 does', async () => {
    expect(await challengeOf('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
  });

  it('reads the nonce of an ID token, and nothing from something that is not one', () => {
    expect(nonceOf(idToken({ nonce: 'nonce-1' }))).toBe('nonce-1');
    expect(nonceOf('not-a-token')).toBeUndefined();
  });

  it('swaps the code for an ID token and signs in to KApp as the portal', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ json: () => Promise.resolve({ id_token: idToken({ nonce: 'nonce-1' }) }) });
    vi.stubGlobal('fetch', fetchMock);

    const done = service.complete(new URLSearchParams({ code: 'the-code', state: 'state-1' }));
    // The call to KApp leaves only after Microsoft's token endpoint has answered.
    const signIn = await vi.waitFor(() => http.expectOne('http://localhost:8080/auth/microsoft'));
    expect(signIn.request.body).toEqual({ idToken: idToken({ nonce: 'nonce-1' }), client: 'PORTAL' });
    const tokens = { accessToken: 'kapp-access-token', tokenType: 'Bearer', expiresIn: 3600, refreshToken: 'rt_1', refreshExpiresIn: 43200, userId: 'u', roles: ['ROLE_STAFF', 'ROLE_ADMIN'] };
    signIn.flush(tokens);

    expect(await done).toBe('/data/users');
    expect(start).toHaveBeenCalledWith(tokens);
    const sent = fetchMock.mock.calls[0][1].body as URLSearchParams;
    expect(sent.get('code_verifier')).toBe(PENDING.verifier);
    expect(sent.get('grant_type')).toBe('authorization_code');
  });

  it('refuses a sign-in it did not start: the state does not match', async () => {
    await expect(service.complete(new URLSearchParams({ code: 'the-code', state: 'someone-else' }))).rejects.toThrow();
    expect(start).not.toHaveBeenCalled();
  });

  it('refuses an ID token from another sign-in: the nonce does not match', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ json: () => Promise.resolve({ id_token: idToken({ nonce: 'other' }) }) }));

    await expect(service.complete(new URLSearchParams({ code: 'the-code', state: 'state-1' }))).rejects.toThrow();
    http.expectNone('http://localhost:8080/auth/microsoft');
  });

  it('uses a state only once', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ json: () => Promise.resolve({}) }));
    await expect(service.complete(new URLSearchParams({ code: 'c', state: 'state-1' }))).rejects.toThrow();

    await expect(service.complete(new URLSearchParams({ code: 'c', state: 'state-1' }))).rejects.toThrow(/not started here/);
  });

  it('passes on what Microsoft said when it refused', async () => {
    await expect(service.complete(new URLSearchParams({ error: 'access_denied', error_description: 'The user cancelled.' })))
      .rejects.toThrow('The user cancelled.');
  });
});

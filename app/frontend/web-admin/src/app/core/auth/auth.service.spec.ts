import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiConfigService } from '../config/api-config.service';
import { AuthService } from './auth.service';
import { TokenStore } from './token.store';

const RENEWED = {
  accessToken: 'new.access.token',
  tokenType: 'Bearer',
  expiresIn: 3600,
  refreshToken: 'rt_2',
  refreshExpiresIn: 43200,
  userId: 'u',
  roles: ['ROLE_STAFF', 'ROLE_ADMIN'],
};

describe('AuthService renewal', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  const refreshToken = vi.fn<() => string | null>();
  const start = vi.fn();
  const reload = vi.fn();

  beforeEach(() => {
    refreshToken.mockReset().mockReturnValue('rt_1');
    start.mockReset();
    reload.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ApiConfigService, useValue: { baseUrl: () => 'http://localhost:8080' } },
        { provide: TokenStore, useValue: { refreshToken, start, reload } },
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  it('swaps the refresh token for the next session', async () => {
    const renewed = auth.renew();

    const call = http.expectOne('http://localhost:8080/auth/refresh');
    expect(call.request.body).toEqual({ refreshToken: 'rt_1' });
    call.flush(RENEWED);

    expect(await renewed).toBe(true);
    expect(start).toHaveBeenCalledWith(RENEWED);
  });

  // The server rotates the token: a second request with the same one reads as a stolen copy
  // and revokes the whole session.
  it('sends one renewal however many ask for it at once', async () => {
    const first = auth.renew();
    const second = auth.renew();

    http.expectOne('http://localhost:8080/auth/refresh').flush(RENEWED);

    expect(await first).toBe(true);
    expect(await second).toBe(true);
    expect(start).toHaveBeenCalledTimes(1);
  });

  it('says the session is over when the server refuses the refresh token', async () => {
    const renewed = auth.renew();

    http.expectOne('http://localhost:8080/auth/refresh').flush(
      { status: 401, error: 'Unauthorized', message: 'Sign in again' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(await renewed).toBe(false);
    expect(start).not.toHaveBeenCalled();
  });

  it('asks nothing of the server when there is no refresh token', async () => {
    refreshToken.mockReturnValue(null);

    expect(await auth.renew()).toBe(false);
    http.expectNone('http://localhost:8080/auth/refresh');
  });

  // Sending the refresh token another tab already used would revoke the session in every tab.
  it('takes the session another tab renewed while this one waited', async () => {
    refreshToken.mockReturnValueOnce('rt_1').mockReturnValue('rt_2');

    expect(await auth.renew()).toBe(true);
    expect(reload).toHaveBeenCalled();
    http.expectNone('http://localhost:8080/auth/refresh');
  });

  it('renews holding the lock every tab of the portal shares', async () => {
    const request = vi.fn((_name: string, work: () => Promise<boolean>) => work());
    vi.stubGlobal('navigator', { locks: { request } });

    const renewed = auth.renew();
    await vi.waitFor(() => http.expectOne('http://localhost:8080/auth/refresh').flush(RENEWED));

    expect(await renewed).toBe(true);
    expect(request).toHaveBeenCalledWith('kapp-admin:renewal', expect.any(Function));
    vi.unstubAllGlobals();
  });
});

describe('AuthService sign-out', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  const refreshToken = vi.fn<() => string | null>();
  const clear = vi.fn();

  beforeEach(() => {
    refreshToken.mockReset().mockReturnValue('rt_1');
    clear.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ApiConfigService, useValue: { baseUrl: () => 'http://localhost:8080' } },
        { provide: TokenStore, useValue: { refreshToken, clear } },
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  it('revokes the session on the server, so a copy of the refresh token cannot renew it', () => {
    auth.logout();

    const call = http.expectOne('http://localhost:8080/auth/logout');
    expect(call.request.body).toEqual({ refreshToken: 'rt_1' });
    call.flush(null, { status: 204, statusText: 'No Content' });
    expect(clear).toHaveBeenCalled();
  });

  // A gateway that is down must not keep anybody signed in.
  it('signs out here even when the server cannot be reached', () => {
    auth.logout();

    http.expectOne('http://localhost:8080/auth/logout').error(new ProgressEvent('error'));
    expect(clear).toHaveBeenCalled();
  });

  it('asks nothing of the server with no refresh token', () => {
    refreshToken.mockReturnValue(null);

    auth.logout();

    http.expectNone('http://localhost:8080/auth/logout');
    expect(clear).toHaveBeenCalled();
  });
});

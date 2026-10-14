import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth/auth.service';
import { MicrosoftSignInService, type SignInOptions } from '../../core/auth/microsoft-sign-in.service';
import { AppHttpError } from '../../core/http/api-http-error';
import type { ApiError } from '../../core/http/api-error.model';
import { LoginPage, asksForNewPassword, notForThisPortal } from './login.page';

/** A server with passwords on and no Microsoft, as the development servers were before. */
const passwordsOnly = microsoftAnswering({ microsoft: null, localSignIn: true });

function microsoftAnswering(options: SignInOptions | Error, start = vi.fn(() => Promise.resolve())) {
  const answer = options instanceof Error ? Promise.reject(options) : Promise.resolve(options);
  return { provide: MicrosoftSignInService, useValue: { options: () => answer, start } };
}

const temporary: ApiError = {
  timestamp: '2026-10-01T15:00:00Z',
  status: 403,
  error: 'Forbidden',
  message: 'This password is temporary: choose a new one to sign in',
  path: '/auth/login',
  details: [{ field: 'newPassword', issue: 'Required: the password is temporary. Send it with a new one to POST /auth/password' }],
};

describe('signing in with a temporary password', () => {
  it('tells the temporary password apart from an unconfirmed address, which is a 403 too', () => {
    expect(asksForNewPassword(temporary)).toBe(true);
    expect(asksForNewPassword({ ...temporary, details: [] })).toBe(false);
    expect(asksForNewPassword({ ...temporary, status: 401 })).toBe(false);
    expect(asksForNewPassword(null)).toBe(false);
  });

  it('asks for a password of their own, and sends it with the temporary one', () => {
    const login = vi.fn(() => throwError(() => new AppHttpError(temporary)));
    const changePassword = vi.fn(() => of({ accessToken: 't', tokenType: 'Bearer', expiresIn: 3600, userId: 'u', roles: ['ROLE_ADMIN'] }));
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([{ path: '**', children: [] }]), { provide: AuthService, useValue: { login, changePassword } }, passwordsOnly],
    });
    const page = TestBed.createComponent(LoginPage).componentInstance;

    page.form.setValue({ email: 'ana.ruiz@konradlorenz.edu.co', password: 'K7QM-X2RP-94TB' });
    page.submit();
    expect(page.choosing()).toBe(true);
    expect(page.error()).toBeNull();

    page.newPassword.setValue({ password: 'MyOwnPassword2026', repeat: 'MyOwnPasswordTypo' });
    page.choose();
    expect(changePassword).not.toHaveBeenCalled();

    page.newPassword.setValue({ password: 'MyOwnPassword2026', repeat: 'MyOwnPassword2026' });
    page.choose();
    expect(changePassword).toHaveBeenCalledWith('ana.ruiz@konradlorenz.edu.co', 'K7QM-X2RP-94TB', 'MyOwnPassword2026');
  });

  it('stops at sign-in for an account that is not an administrator, temporary password or not', () => {
    const refused: ApiError = { ...temporary, message: 'This account cannot sign in here', details: [{ field: 'allowedRoles', issue: 'The account has none of these roles. Nothing was changed' }] };
    expect(notForThisPortal(refused)).toBe(true);
    expect(notForThisPortal(temporary)).toBe(false);

    const login = vi.fn(() => throwError(() => new AppHttpError(refused)));
    const changePassword = vi.fn();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([{ path: '**', children: [] }]), { provide: AuthService, useValue: { login, changePassword } }, passwordsOnly],
    });
    const page = TestBed.createComponent(LoginPage).componentInstance;
    page.form.setValue({ email: 'pepe.veras@konradlorenz.edu.co', password: 'K7QM-X2RP-94TB' });
    page.submit();

    expect(page.notAdmin()).toBe(true);
    expect(page.choosing()).toBe(false);
    expect(changePassword).not.toHaveBeenCalled();
  });
});

describe('the sign-in options of the server', () => {
  const microsoft = { authority: 'http://localhost:8090/kapp', clientId: 'kapp-portal-dev' };

  function pageWith(provider: ReturnType<typeof microsoftAnswering>) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideRouter([{ path: '**', children: [] }]), { provide: AuthService, useValue: {} }, provider],
    });
    return TestBed.createComponent(LoginPage).componentInstance;
  }

  it('offers Microsoft and hides the passwords where the server keeps them closed', async () => {
    const page = pageWith(microsoftAnswering({ microsoft, localSignIn: false }));

    await vi.waitFor(() => expect(page.options()).toBeTruthy());
    expect(page.options()?.microsoft).toEqual(microsoft);
    expect(page.passwords()).toBe(false);
  });

  it('offers both on a development server', async () => {
    const page = pageWith(microsoftAnswering({ microsoft, localSignIn: true }));

    await vi.waitFor(() => expect(page.passwords()).toBe(true));
    expect(page.options()?.microsoft).toEqual(microsoft);
  });

  it('falls back to the password form when the server cannot say', async () => {
    const page = pageWith(microsoftAnswering(new Error('no such route')));

    await vi.waitFor(() => expect(page.options()).toBeNull());
    expect(page.passwords()).toBe(true);
  });

  it('leaves for Microsoft with the page to come back to, and says so when it cannot', async () => {
    const start = vi.fn(() => Promise.reject(new Error('discovery failed')));
    const page = pageWith(microsoftAnswering({ microsoft, localSignIn: false }, start));

    page.signInWithMicrosoft(microsoft);

    expect(start).toHaveBeenCalledWith(microsoft, '/my-token');
    await vi.waitFor(() => expect(page.microsoftError()).toBeTruthy());
    expect(page.leaving()).toBe(false);
  });
});

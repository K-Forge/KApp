import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { MicrosoftSignInService } from '../../core/auth/microsoft-sign-in.service';
import { AppHttpError } from '../../core/http/api-http-error';
import { MicrosoftCallbackPage, noPortalPermission } from './microsoft-callback.page';

function pageWith(complete: () => Promise<string>) {
  TestBed.configureTestingModule({
    imports: [MicrosoftCallbackPage],
    providers: [provideRouter([]), { provide: MicrosoftSignInService, useValue: { complete } }],
  });
  const fixture = TestBed.createComponent(MicrosoftCallbackPage);
  return { page: fixture.componentInstance, router: TestBed.inject(Router) };
}

describe('MicrosoftCallbackPage', () => {
  it('goes back to the page the person was on once signed in', async () => {
    const { page, router } = pageWith(() => Promise.resolve('/data/users'));
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    page.ngOnInit();

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/data/users'));
    expect(page.failed()).toBe(false);
  });

  it('says plainly that an account with no permission cannot use the portal', async () => {
    const refused = new AppHttpError({
      timestamp: '', status: 403, error: 'Forbidden', message: 'no permission', path: '/auth/microsoft',
      details: [{ field: 'client', issue: 'PORTAL takes an account with a permission' }],
    });
    const { page } = pageWith(() => Promise.reject(refused));

    page.ngOnInit();

    await vi.waitFor(() => expect(page.failed()).toBe(true));
    expect(page.noPermission()).toBe(true);
  });

  it('shows what went wrong when the sign-in itself failed', async () => {
    const { page } = pageWith(() => Promise.reject(new Error('The user cancelled.')));

    page.ngOnInit();

    await vi.waitFor(() => expect(page.failed()).toBe(true));
    expect(page.message()).toBe('The user cancelled.');
    expect(page.noPermission()).toBe(false);
  });

  it('tells the no-permission 403 from the others', () => {
    expect(noPortalPermission({ timestamp: '', status: 403, error: '', message: '', path: '', details: [{ field: 'client', issue: '' }] })).toBe(true);
    expect(noPortalPermission({ timestamp: '', status: 403, error: '', message: 'deactivated', path: '' })).toBe(false);
  });
});

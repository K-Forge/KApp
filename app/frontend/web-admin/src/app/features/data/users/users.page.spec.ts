import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { TokenStore } from '../../../core/auth/token.store';
import { InvitationCodesService } from '../invitation-codes/invitation-codes.service';
import type { InvitationCodeRequest } from '../invitation-codes/invitation-code.model';
import { UsersPage } from './users.page';
import type { UserProfile } from './user.model';

function profile(id: string): UserProfile {
  return {
    id,
    email: `${id}@kforge.dev`,
    firstName: 'A',
    lastName: 'B',
    identification: null,
    phone: null,
    avatarUrl: null,
    role: 'ROLE_ADMIN',
    active: true,
    academic: null,
  };
}

describe('UsersPage', () => {
  let page: UsersPage;
  const create = vi.fn((request: InvitationCodeRequest) =>
    of({ code: 'KL-7K2M-QX9P', role: request.role, maxUses: request.maxUses, timesUsed: 0, active: true, expiresAt: request.expiresAt ?? null, notes: request.notes ?? null }),
  );

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UsersPage],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        {
          provide: TokenStore,
          useValue: { decoded: () => ({ claims: { sub: 'me' } }) },
        },
        { provide: InvitationCodesService, useValue: { create } },
      ],
    }).compileComponents();
    page = TestBed.createComponent(UsersPage).componentInstance;
  });

  // Deactivating now genuinely removes access, and an invitation is a code rather than an
  // account, so switching your own account off means editing MongoDB by hand to get back in.
  it('will not let you switch off your own account', () => {
    expect(page.isSelf(profile('me'))).toBe(true);
  });

  it('still lets you switch off somebody else', () => {
    expect(page.isSelf(profile('someone-else'))).toBe(false);
  });

  it('invites one person with a code that creates one account, written down as theirs', () => {
    page.inviteForm.setValue({ name: ' Ana Ruiz ', email: 'ana.ruiz@konradlorenz.edu.co', role: 'ROLE_PROFESSOR', days: 7 });
    const before = Date.now();

    page.sendInvite();

    const request = create.mock.calls.at(-1)![0];
    expect(request).toMatchObject({ role: 'ROLE_PROFESSOR', maxUses: 1, notes: 'For Ana Ruiz <ana.ruiz@konradlorenz.edu.co>' });
    const days = (new Date(request.expiresAt!).getTime() - before) / 86_400_000;
    expect(days).toBeGreaterThan(6.99);
    expect(days).toBeLessThan(7.01);
    expect(page.invited()).toMatchObject({ code: 'KL-7K2M-QX9P', name: 'Ana Ruiz', role: 'ROLE_PROFESSOR' });
    expect(page.message()).toContain('KL-7K2M-QX9P');
    expect(page.message()).toContain('Ana Ruiz');
  });

  it('asks for a name and a real e-mail before inviting', () => {
    create.mockClear();
    page.inviteForm.setValue({ name: '', email: 'not an e-mail', role: 'ROLE_STUDENT', days: 14 });

    page.sendInvite();

    expect(create).not.toHaveBeenCalled();
    expect(page.invalid('name')).toBe(true);
    expect(page.invalid('email')).toBe(true);
  });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { TokenStore } from '../../../core/auth/token.store';
import { AccountsService, type AccountRequest } from './accounts.service';
import { UsersPage } from './users.page';
import type { DirectoryEntry } from './user.model';

function profile(id: string): DirectoryEntry {
  return {
    id,
    email: `${id}@kforge.dev`,
    firstName: 'A',
    lastName: 'B',
    avatarUrl: null,
    roles: ['ROLE_STAFF', 'ROLE_ADMIN'],
    active: true,
  };
}

describe('UsersPage', () => {
  let page: UsersPage;
  const issued = (email: string, role: string) => ({
    userId: 'u-1',
    email,
    roles: [role],
    temporaryPassword: 'K7QM-X2RP-94TB',
    expiresAt: '2026-10-08T15:00:00Z',
  });
  const create = vi.fn((request: AccountRequest) => of(issued(request.email, request.profileRole)));
  const issueTemporaryPassword = vi.fn(() => of(issued('pepita@kforge.dev', 'ROLE_STUDENT')));
  const deleteAccount = vi.fn(() => of(undefined));

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
        { provide: AccountsService, useValue: { create, issueTemporaryPassword, delete: deleteAccount } },
      ],
    }).compileComponents();
    page = TestBed.createComponent(UsersPage).componentInstance;
  });

  // Deactivating now genuinely removes access, so switching your own account off means editing
  // MongoDB by hand to get back in.
  it('will not let you switch off your own account', () => {
    expect(page.isSelf(profile('me'))).toBe(true);
  });

  it('still lets you switch off somebody else', () => {
    expect(page.isSelf(profile('someone-else'))).toBe(false);
  });

  it('creates a professor account and shows its temporary password, with the message to send', () => {
    page.createForm.setValue({ firstName: ' Ana ', lastName: 'Ruiz', email: 'ana.ruiz@konradlorenz.edu.co', profileRole: 'ROLE_PROFESSOR' });

    page.createAccount();

    expect(create.mock.calls.at(-1)![0]).toEqual({ email: 'ana.ruiz@konradlorenz.edu.co', firstName: 'Ana', lastName: 'Ruiz', profileRole: 'ROLE_PROFESSOR' });
    expect(page.issued()).toMatchObject({ name: 'Ana Ruiz', created: true, password: { temporaryPassword: 'K7QM-X2RP-94TB' } });
    expect(page.message()).toContain('K7QM-X2RP-94TB');
    expect(page.message()).toContain('ana.ruiz@konradlorenz.edu.co');
  });

  it('creates a student or an administrative employee with no code at all: KApp keeps none', () => {
    for (const profileRole of ['ROLE_STUDENT', 'ROLE_STAFF'] as const) {
      page.createForm.setValue({ firstName: 'Pepito', lastName: 'Perez', email: 'pepito@konradlorenz.edu.co', profileRole });
      page.createAccount();
      expect(create.mock.calls.at(-1)![0]).toEqual({ email: 'pepito@konradlorenz.edu.co', firstName: 'Pepito', lastName: 'Perez', profileRole });
    }
  });

  it('gives somebody else a new temporary password once it is confirmed', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    page.newTemporaryPassword(profile('someone-else'));
    expect(issueTemporaryPassword).toHaveBeenCalledWith('someone-else');
    expect(page.issued()).toMatchObject({ created: false });
  });

  it('deletes the account of somebody else once it is confirmed, and asks before it', () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    page.deleteAccount(profile('someone-else'));
    expect(deleteAccount).not.toHaveBeenCalled();

    confirm.mockReturnValue(true);
    page.deleteAccount(profile('someone-else'));
    expect(deleteAccount).toHaveBeenCalledWith('someone-else');
  });
});

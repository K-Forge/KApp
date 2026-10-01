import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { TokenStore } from '../../../core/auth/token.store';
import { AccountsService, type AccountRequest } from './accounts.service';
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
  const issued = (email: string, role: string) => ({
    userId: 'u-1',
    email,
    role,
    temporaryPassword: 'K7QM-X2RP-94TB',
    expiresAt: '2026-10-08T15:00:00Z',
  });
  const create = vi.fn((request: AccountRequest) => of(issued(request.email, request.role)));
  const issueTemporaryPassword = vi.fn(() => of(issued('pepita@kforge.dev', 'ROLE_STUDENT')));

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
        { provide: AccountsService, useValue: { create, issueTemporaryPassword } },
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
    page.createForm.setValue({ firstName: ' Ana ', lastName: 'Ruiz', email: 'ana.ruiz@konradlorenz.edu.co', role: 'ROLE_PROFESSOR', studentCode: '', programCode: '' });

    page.createAccount();

    expect(create.mock.calls.at(-1)![0]).toEqual({ email: 'ana.ruiz@konradlorenz.edu.co', firstName: 'Ana', lastName: 'Ruiz', role: 'ROLE_PROFESSOR' });
    expect(page.issued()).toMatchObject({ name: 'Ana Ruiz', created: true, password: { temporaryPassword: 'K7QM-X2RP-94TB' } });
    expect(page.message()).toContain('K7QM-X2RP-94TB');
    expect(page.message()).toContain('ana.ruiz@konradlorenz.edu.co');
  });

  it('asks a student account for its two codes before creating it', () => {
    create.mockClear();
    page.createForm.setValue({ firstName: 'Pepito', lastName: 'Perez', email: 'pepito@konradlorenz.edu.co', role: 'ROLE_STUDENT', studentCode: '', programCode: '' });

    page.createAccount();
    expect(create).not.toHaveBeenCalled();
    expect(page.invalid('studentCode')).toBe(true);

    page.createForm.patchValue({ studentCode: '506999999', programCode: '506' });
    page.createAccount();
    expect(create.mock.calls.at(-1)![0]).toMatchObject({ role: 'ROLE_STUDENT', studentCode: '506999999', programCode: '506' });
  });

  it('gives somebody else a new temporary password once it is confirmed', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    page.newTemporaryPassword(profile('someone-else'));
    expect(issueTemporaryPassword).toHaveBeenCalledWith('someone-else');
    expect(page.issued()).toMatchObject({ created: false });
  });
});

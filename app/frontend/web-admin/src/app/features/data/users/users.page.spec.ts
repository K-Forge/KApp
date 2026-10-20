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
  const setRoles = vi.fn((userId: string, request: { profileRole: string; permissions: string[] }) =>
    of({ userId, roles: [request.profileRole, ...request.permissions] }));

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
        { provide: AccountsService, useValue: { create, issueTemporaryPassword, delete: deleteAccount, setRoles } },
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

  it('opens the permissions of an account as it holds them, and sends exactly what is chosen', () => {
    const someone = { ...profile('someone-else'), roles: ['ROLE_PROFESSOR', 'ROLE_RECEPTION'] } as DirectoryEntry;
    page.result.set({ content: [someone], page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true });

    page.editRoles(someone);
    expect(page.profileRole()).toBe('ROLE_PROFESSOR');
    expect(page.chosen()).toEqual(['ROLE_RECEPTION']);

    page.toggle('ROLE_RECEPTION');
    page.toggle('ROLE_WELLBEING');
    page.toggle('ROLE_ADMIN');
    page.saveRoles();

    // In the contract's order, whatever order they were ticked in.
    expect(setRoles).toHaveBeenCalledWith('someone-else', { profileRole: 'ROLE_PROFESSOR', permissions: ['ROLE_ADMIN', 'ROLE_WELLBEING'] });
    expect(page.result()?.content[0].roles).toEqual(['ROLE_PROFESSOR', 'ROLE_ADMIN', 'ROLE_WELLBEING']);
  });

  // The server refuses it (400); the box is locked so nobody has to find out that way.
  it('locks your own administration, and only that', () => {
    expect(page.locked(profile('me'), 'ROLE_ADMIN')).toBe(true);
    expect(page.locked(profile('me'), 'ROLE_RECEPTION')).toBe(false);
    expect(page.locked(profile('someone-else'), 'ROLE_ADMIN')).toBe(false);
  });

  // Saving went through the form's own submit, which the page has to catch: a plain form with no
  // formGroup gets no ngSubmit, and the browser reloaded the page instead of sending anything.
  it('saves when the dialog form is submitted, without leaving the page', () => {
    // jsdom has no <dialog> methods; the dialog closes after a save.
    const proto = HTMLDialogElement.prototype as { close?: () => void };
    const had = proto.close;
    proto.close ??= () => undefined;
    const fixture = TestBed.createComponent(UsersPage);
    const users = fixture.componentInstance;
    users.editing.set(profile('someone-else'));
    users.chosen.set(['ROLE_RECEPTION']);
    fixture.detectChanges();
    setRoles.mockClear();

    const submit = new Event('submit', { cancelable: true });
    fixture.nativeElement.querySelector('form.roles-form').dispatchEvent(submit);

    expect(submit.defaultPrevented).toBe(true);
    expect(setRoles).toHaveBeenCalledWith('someone-else', { profileRole: 'ROLE_STAFF', permissions: ['ROLE_RECEPTION'] });
    proto.close = had;
  });
});

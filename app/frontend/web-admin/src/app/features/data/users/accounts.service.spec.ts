import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ApiClientService } from '../../../core/http/api-client.service';
import { AccountsService } from './accounts.service';

describe('AccountsService', () => {
  it('replaces an account roles with exactly the profile role and the permissions sent', () => {
    const put = vi.fn(() => of({ userId: 'u 1', roles: ['ROLE_STAFF', 'ROLE_RECEPTION'] }));
    TestBed.configureTestingModule({ providers: [{ provide: ApiClientService, useValue: { put } }] });

    let roles: string[] = [];
    TestBed.inject(AccountsService)
      .setRoles('u 1', { profileRole: 'ROLE_STAFF', permissions: ['ROLE_RECEPTION'] })
      .subscribe((answer) => (roles = answer.roles));

    expect(put).toHaveBeenCalledWith('/auth/admin/accounts/u%201/roles', { profileRole: 'ROLE_STAFF', permissions: ['ROLE_RECEPTION'] });
    expect(roles).toEqual(['ROLE_STAFF', 'ROLE_RECEPTION']);
  });
});

import { inject, Injectable } from '@angular/core';
import type { Observable } from 'rxjs';
import { ApiClientService } from '../../../core/http/api-client.service';

/** The profile roles an account an administrator creates may have: never a permission, as with codes. */
export const ACCOUNT_ROLES = ['ROLE_STUDENT', 'ROLE_PROFESSOR', 'ROLE_STAFF'] as const;
export type AccountRole = (typeof ACCOUNT_ROLES)[number];

/** Mirrors AccountRequest in docs/api/auth.openapi.yaml. KApp keeps no student code. */
export interface AccountRequest {
  email: string;
  firstName: string;
  lastName: string;
  profileRole: AccountRole;
}

/** Mirrors AccountRolesRequest: one profile role and every permission the account should hold. */
export interface AccountRolesRequest {
  profileRole: AccountRole;
  permissions: string[];
}

/** Mirrors AccountRoles: the account's roles now, the profile role first. */
export interface AccountRoles {
  userId: string;
  roles: string[];
}

/** Mirrors TemporaryPassword: the only answer the password appears in, since only its hash is kept. */
export interface TemporaryPassword {
  userId: string;
  email: string;
  roles: string[];
  temporaryPassword: string;
  expiresAt: string;
}

/** Accounts an administrator creates, with a temporary password the person replaces at first sign-in. */
@Injectable({ providedIn: 'root' })
export class AccountsService {
  private readonly api = inject(ApiClientService);

  create(request: AccountRequest): Observable<TemporaryPassword> {
    return this.api.post<TemporaryPassword>('/auth/admin/accounts', request);
  }

  /** The account and its profile, gone. Never an administrator's, never one's own: the server refuses. */
  delete(userId: string): Observable<void> {
    return this.api.delete(`/auth/admin/accounts/${encodeURIComponent(userId)}`);
  }

  /**
   * Replaces the account's roles with exactly these. They reach the person's tokens at their next
   * renewal, within the hour. The server refuses an administrator taking `ROLE_ADMIN` from themselves.
   */
  setRoles(userId: string, request: AccountRolesRequest): Observable<AccountRoles> {
    return this.api.put<AccountRoles>(`/auth/admin/accounts/${encodeURIComponent(userId)}/roles`, request);
  }

  /** A new temporary password for somebody who forgot theirs: the one they had stops working. */
  issueTemporaryPassword(userId: string): Observable<TemporaryPassword> {
    return this.api.post<TemporaryPassword>(`/auth/admin/accounts/${encodeURIComponent(userId)}/temporary-password`, {});
  }
}

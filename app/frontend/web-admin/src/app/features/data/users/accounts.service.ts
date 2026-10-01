import { inject, Injectable } from '@angular/core';
import type { Observable } from 'rxjs';
import { ApiClientService } from '../../../core/http/api-client.service';

/** The roles an account an administrator creates may have: never ROLE_ADMIN, as with codes. */
export const ACCOUNT_ROLES = ['ROLE_STUDENT', 'ROLE_PROFESSOR'] as const;
export type AccountRole = (typeof ACCOUNT_ROLES)[number];

/** Mirrors AccountRequest in docs/api/auth.openapi.yaml. A student needs its two codes. */
export interface AccountRequest {
  email: string;
  firstName: string;
  lastName: string;
  role: AccountRole;
  studentCode?: string;
  programCode?: string;
}

/** Mirrors TemporaryPassword: the only answer the password appears in, since only its hash is kept. */
export interface TemporaryPassword {
  userId: string;
  email: string;
  role: string;
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

  /** A new temporary password for somebody who forgot theirs: the one they had stops working. */
  issueTemporaryPassword(userId: string): Observable<TemporaryPassword> {
    return this.api.post<TemporaryPassword>(`/auth/admin/accounts/${encodeURIComponent(userId)}/temporary-password`, {});
  }
}

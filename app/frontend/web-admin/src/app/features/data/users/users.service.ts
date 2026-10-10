import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../../../core/http/api-client.service';
import type { PageResponse } from '../../../core/http/page-response.model';
import type { DirectoryEntry, UserListFilters } from './user.model';

/** Thin wrapper over /api/users - list/search, read one, and the activation toggle. */
@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly api = inject(ApiClientService);

  list(filters: UserListFilters): Observable<PageResponse<DirectoryEntry>> {
    return this.api.get<PageResponse<DirectoryEntry>>('/api/users', {
      page: filters.page,
      size: filters.size,
      role: filters.role,
      active: filters.active,
      q: filters.q,
    });
  }

  getById(userId: string): Observable<DirectoryEntry> {
    return this.api.get<DirectoryEntry>(`/api/users/${encodeURIComponent(userId)}`);
  }

  setStatus(userId: string, active: boolean): Observable<DirectoryEntry> {
    return this.api.patch<DirectoryEntry>(`/api/users/${encodeURIComponent(userId)}/status`, { active });
  }
}

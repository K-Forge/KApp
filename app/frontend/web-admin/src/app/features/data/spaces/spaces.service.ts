import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../../../core/http/api-client.service';
import type { PageResponse } from '../../../core/http/page-response.model';
import type { Space, SpaceRequest, SpaceSearchFilters } from './space.model';

/** /api/map/spaces - full-text search plus CRUD, ROLE_ADMIN for writes. */
@Injectable({ providedIn: 'root' })
export class SpacesService {
  private readonly api = inject(ApiClientService);

  search(filters: SpaceSearchFilters): Observable<PageResponse<Space>> {
    return this.api.get<PageResponse<Space>>('/api/map/spaces/search', {
      q: filters.q,
      page: filters.page,
      size: filters.size,
      campus: filters.campus,
      type: filters.type,
      category: filters.category,
      buildingCode: filters.buildingCode,
      wing: filters.wing,
      floor: filters.floor,
    });
  }

  create(request: SpaceRequest): Observable<Space> {
    return this.api.post<Space>('/api/map/spaces', request);
  }

  // buildingCode disambiguates a room code that exists in more than one building - always sent
  // since a search result already carries the space's real building unambiguously.
  update(code: string, buildingCode: string, request: SpaceRequest): Observable<Space> {
    return this.api.put<Space>(`/api/map/spaces/${encodeURIComponent(code)}`, request, { buildingCode });
  }

  delete(code: string, buildingCode: string): Observable<void> {
    return this.api.delete(`/api/map/spaces/${encodeURIComponent(code)}`, { buildingCode });
  }
}

import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../../../core/http/api-client.service';
import type { SpaceType } from './space.model';

/** /api/map/space-types - the catalogue. Readable by any role, writable by ROLE_ADMIN. */
@Injectable({ providedIn: 'root' })
export class SpaceTypesService {
  private readonly api = inject(ApiClientService);

  list(): Observable<SpaceType[]> {
    return this.api.get<SpaceType[]>('/api/map/space-types');
  }

  create(type: SpaceType): Observable<SpaceType> {
    return this.api.post<SpaceType>('/api/map/space-types', type);
  }

  update(type: SpaceType): Observable<SpaceType> {
    return this.api.put<SpaceType>(`/api/map/space-types/${encodeURIComponent(type.code)}`, type);
  }

  delete(code: string): Observable<void> {
    return this.api.delete(`/api/map/space-types/${encodeURIComponent(code)}`);
  }
}

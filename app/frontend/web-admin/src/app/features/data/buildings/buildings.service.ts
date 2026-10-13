import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../../../core/http/api-client.service';
import type { Building, BuildingRequest } from './building.model';

/** /api/map/buildings - full CRUD, ROLE_ADMIN for writes (enforced server side, not re-guessed here). */
@Injectable({ providedIn: 'root' })
export class BuildingsService {
  private readonly api = inject(ApiClientService);

  /** `q` matches the code, the name and every alias, ignoring case and accents. */
  list(campus?: string, q?: string): Observable<Building[]> {
    return this.api.get<Building[]>('/api/map/buildings', { campus, q });
  }

  create(request: BuildingRequest): Observable<Building> {
    return this.api.post<Building>('/api/map/buildings', request);
  }

  update(code: string, request: BuildingRequest): Observable<Building> {
    return this.api.put<Building>(`/api/map/buildings/${encodeURIComponent(code)}`, request);
  }

  delete(code: string): Observable<void> {
    return this.api.delete(`/api/map/buildings/${encodeURIComponent(code)}`);
  }
}

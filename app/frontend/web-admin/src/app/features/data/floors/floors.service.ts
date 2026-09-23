import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../../../core/http/api-client.service';
import type { Building } from '../buildings/building.model';
import type { FloorDetail, FloorLayoutRequest } from './floor.model';

/** One floor of a building at a time: read it whole, save it whole. Writes are ROLE_ADMIN. */
@Injectable({ providedIn: 'root' })
export class FloorsService {
  private readonly api = inject(ApiClientService);

  building(code: string): Observable<Building> {
    return this.api.get<Building>(`/api/map/buildings/${encodeURIComponent(code)}`);
  }

  get(buildingCode: string, floorCode: string): Observable<FloorDetail> {
    return this.api.get<FloorDetail>(
      `/api/map/buildings/${encodeURIComponent(buildingCode)}/floors/${encodeURIComponent(floorCode)}`,
    );
  }

  saveLayout(buildingCode: string, floorCode: string, request: FloorLayoutRequest): Observable<FloorDetail> {
    return this.api.put<FloorDetail>(
      `/api/map/buildings/${encodeURIComponent(buildingCode)}/floors/${encodeURIComponent(floorCode)}/layout`,
      request,
    );
  }
}

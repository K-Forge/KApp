import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../../../core/http/api-client.service';
import type { Structures, StructuresRequest } from './structure.model';

/** /api/map/campuses/{campus}/structures - read by any map reader, saved whole by ROLE_ADMIN. */
@Injectable({ providedIn: 'root' })
export class StructuresService {
  private readonly api = inject(ApiClientService);

  forCampus(campus: string): Observable<Structures> {
    return this.api.get<Structures>(`/api/map/campuses/${encodeURIComponent(campus)}/structures`);
  }

  /** Refused with 409 when somebody saved the list after `request.version` was read. */
  save(campus: string, request: StructuresRequest): Observable<Structures> {
    return this.api.put<Structures>(`/api/map/campuses/${encodeURIComponent(campus)}/structures`, request);
  }
}

import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../../../core/http/api-client.service';
import type { Pensum, PensumSummary } from './pensum.model';

/** /api/catalog/pensums — read-only: the catalog is SINU's, and its backup arrives by import. */
@Injectable({ providedIn: 'root' })
export class PensumsService {
  private readonly api = inject(ApiClientService);

  /** Summaries only — see PensumSummary. Not paginated: the catalogue is a few dozen rows. */
  list(): Observable<PensumSummary[]> {
    return this.api.get<PensumSummary[]>('/api/catalog/pensums');
  }

  getByCode(pensumCode: string): Observable<Pensum> {
    return this.api.get<Pensum>(`/api/catalog/pensums/${encodeURIComponent(pensumCode)}`);
  }
}

import { inject, Injectable } from '@angular/core';
import type { Observable } from 'rxjs';
import { ApiClientService } from '../../../core/http/api-client.service';
import type { Program } from './program.model';

/** /api/catalog/programs — read-only: the catalog is SINU's. */
@Injectable({ providedIn: 'root' })
export class ProgramsService {
  private readonly api = inject(ApiClientService);

  list(): Observable<Program[]> {
    return this.api.get<Program[]>('/api/catalog/programs');
  }
}

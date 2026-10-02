import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClientService } from '../../../core/http/api-client.service';
import type { Survey, SurveyRequest } from './survey.model';

/** /api/map/campuses/{campus}/survey - read by any map reader, saved whole by ROLE_ADMIN. */
@Injectable({ providedIn: 'root' })
export class SurveyService {
  private readonly api = inject(ApiClientService);

  forCampus(campus: string): Observable<Survey> {
    return this.api.get<Survey>(`/api/map/campuses/${encodeURIComponent(campus)}/survey`);
  }

  /** Refused with 409 when it was saved from somewhere else after `request.version` was read. */
  save(campus: string, request: SurveyRequest): Observable<Survey> {
    return this.api.put<Survey>(`/api/map/campuses/${encodeURIComponent(campus)}/survey`, request);
  }
}

import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, shareReplay } from 'rxjs';
import { ApiClientService } from '../../../core/http/api-client.service';
import type { Ground } from './ground.model';

/**
 * The ground around each campus. Reference data that changes with a deploy, not with an edit, so
 * each campus is fetched once per session; a campus with none answers null.
 */
@Injectable({ providedIn: 'root' })
export class GroundService {
  private readonly api = inject(ApiClientService);
  private readonly cache = new Map<string, Observable<Ground | null>>();

  forCampus(campus: string): Observable<Ground | null> {
    let ground = this.cache.get(campus);
    if (!ground) {
      ground = this.api.get<Ground>(`/api/map/campuses/${encodeURIComponent(campus)}/ground`).pipe(
        // A failure is not remembered: the next floor opened asks again.
        catchError(() => {
          this.cache.delete(campus);
          return of(null);
        }),
        shareReplay(1),
      );
      this.cache.set(campus, ground);
    }
    return ground;
  }
}

import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { FLOOR_STATUS_LABELS, type Building, type FloorStatus } from '../buildings/building.model';
import { BuildingsService } from '../buildings/buildings.service';

/** Where the floor editor starts: every floor of every building, coloured by how far along it is. */
@Component({
  selector: 'app-floors-page',
  imports: [RouterLink, ApiErrorBannerComponent, PageIntroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        title="Floor editor"
        what="Draw each floor of the campus map and put every space in its place - meant for an iPad, standing on the floor being drawn."
        [can]="[
          'Outline rooms from the evacuation plan, then say which space each one is',
          'Load the rooms an information plaque lists, even as a range, and place them one by one',
          'Draw corridors in the colour they are painted',
          'Mark a floor as a draft or as verified on site'
        ]"
        note="Changes stay on this device until the floor is saved, so a dropped connection loses nothing. If somebody else saves the same floor first, the editor says so and lets you choose whose version stays."
      />

      <app-api-error-banner [error]="error()" />

      @if (loading()) {
        <div class="card empty-state"><p>Loading buildings…</p></div>
      }

      @for (building of buildings(); track building.code) {
        <section class="card building">
          <h2>
            <span class="mono">{{ building.code }}</span> · {{ building.name }}
            @if (building.aliases.length) {
              <span class="text-faint aliases">{{ building.aliases.join(' · ') }}</span>
            }
          </h2>
          <div class="floors">
            @for (floor of building.floors; track floor.code) {
              <a class="floor" [class]="'floor ' + statusClass(floor.status)" [routerLink]="['/data/floors', building.code, floor.code]">
                <span class="floor-code">{{ floor.code }}</span>
                <span class="floor-name">{{ floor.name }}</span>
                <span class="floor-status">{{ statusLabels[floor.status ?? 'UNMAPPED'] }}</span>
              </a>
            }
          </div>
        </section>
      } @empty {
        @if (!loading() && !error()) {
          <div class="card empty-state"><p>No buildings yet. Create one under Buildings first.</p></div>
        }
      }
    </div>
  `,
  styles: `
    .building h2 {
      margin: 0 0 0.75rem;
      font-size: 1.0625rem;
      display: flex;
      gap: 0.5rem;
      align-items: baseline;
      flex-wrap: wrap;
    }
    .aliases {
      font-size: 0.8125rem;
      font-weight: 400;
    }
    .floors {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(9rem, 1fr));
      gap: 0.5rem;
    }
    .floor {
      display: flex;
      flex-direction: column;
      gap: 0.125rem;
      min-height: 4.5rem;
      padding: 0.625rem 0.75rem;
      border: 1px solid var(--border);
      border-left-width: 4px;
      border-radius: var(--radius-sm);
      color: var(--text);
      text-decoration: none;
      background: var(--bg-elevated);
    }
    .floor:hover {
      background: var(--bg-hover);
    }
    .floor.verified {
      border-left-color: var(--success);
    }
    .floor.draft {
      border-left-color: var(--warning);
    }
    .floor.unmapped {
      border-left-color: var(--border-strong);
    }
    .floor-code {
      font-weight: 700;
      font-size: 1.0625rem;
    }
    .floor-name {
      font-size: 0.875rem;
    }
    .floor-status {
      font-size: 0.75rem;
      color: var(--text-muted);
    }
  `,
})
export class FloorsPage {
  private readonly buildingsService = inject(BuildingsService);

  readonly statusLabels = FLOOR_STATUS_LABELS;
  readonly loading = signal(true);
  readonly error = signal<ApiError | null>(null);
  readonly buildings = signal<Building[]>([]);

  constructor() {
    this.buildingsService.list().subscribe({
      next: (buildings) => {
        this.buildings.set(buildings);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }

  statusClass(status: FloorStatus | undefined): string {
    return status === 'VERIFIED' ? 'verified' : status === 'DRAFT' ? 'draft' : 'unmapped';
  }
}

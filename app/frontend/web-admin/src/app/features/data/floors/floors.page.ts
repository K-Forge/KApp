import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { FLOOR_STATUS_LABELS, type Building, type FloorStatus } from '../buildings/building.model';
import { BuildingsService } from '../buildings/buildings.service';
import { thumbnail } from '../buildings/building-colors';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/** Where the floor editor starts: every floor of every building, coloured by how far along it is. */
@Component({
  selector: 'app-floors-page',
  imports: [TranslatePipe, RouterLink, NgTemplateOutlet, ApiErrorBannerComponent, PageIntroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        [title]="'Floor editor' | t"
        [what]="'Draw each floor and put every space in its place, from an iPad on site.' | t"
        [can]="[('Outline rooms from the evacuation plan' | t), ('Load the rooms a plaque lists' | t), ('Draw corridors' | t), ('Mark a floor as a draft or verified' | t)]"
        [note]="'Changes stay on this device until you save. If somebody saved first, you choose which version stays.' | t"
      />

      <app-api-error-banner [error]="error()" />

      @if (loading()) {
        <div class="card empty-state"><p>{{ 'Loading buildings…' | t }}</p></div>
      }

      @if (buildings().length) {
        <!-- What each mark on a floor means, once for every building. -->
        <div class="legend small" [attr.aria-label]="'What the marks on a floor mean' | t">
          @for (status of statuses; track status) {
            <span class="legend-item">
              <span class="mark" [class]="'mark ' + statusClass(status)" aria-hidden="true">
                <ng-container *ngTemplateOutlet="markIcon; context: { $implicit: status }" />
              </span>
              {{ statusLabels[status] | t }}
            </span>
          }
        </div>
      }

      @for (b of rows(); track b.building.code) {
        <section class="card building">
          @if (b.thumb; as thumb) {
            <svg class="thumb" [attr.viewBox]="thumb.viewBox" aria-hidden="true">
              @for (part of thumb.parts; track $index) {
                <path [attr.d]="part.d" [attr.fill]="part.fill" [attr.fill-opacity]="part.opacity" [attr.stroke]="part.fill" />
              }
            </svg>
          } @else {
            <div class="thumb thumb-none" aria-hidden="true">{{ b.building.code }}</div>
          }
          <div class="who">
            <h2>{{ b.building.name }}</h2>
            <div class="facts small">
              <span class="code">{{ b.building.code }}</span>
              @if (b.building.address) {
                <span>{{ b.building.address }}</span>
              }
            </div>
            @if (b.nicknames.length) {
              <div class="text-muted small">{{ 'Known as {names}' | t: { names: b.nicknames.join(', ') } }}</div>
            }
          </div>
          <!-- The floors as a lift's buttons: the highest at the top, each with its mark. -->
          <nav class="lift" [attr.aria-label]="'Floors of {name}' | t: { name: b.building.name }">
            @for (floor of b.floors; track floor.code) {
              <a
                [class]="'stop ' + statusClass(floor.status)"
                [routerLink]="['/data/floors', b.building.code, floor.code]"
                [title]="floor.name + ' · ' + (statusLabels[floor.status ?? 'UNMAPPED'] | t)"
                [attr.aria-label]="floor.name + ' · ' + (statusLabels[floor.status ?? 'UNMAPPED'] | t)"
              >
                <span class="stop-code">{{ floor.code }}</span>
                <span class="mark" aria-hidden="true">
                  <ng-container *ngTemplateOutlet="markIcon; context: { $implicit: floor.status ?? 'UNMAPPED' }" />
                </span>
              </a>
            }
          </nav>
        </section>
      } @empty {
        @if (!loading() && !error()) {
          <div class="card empty-state"><p>{{ 'No buildings yet. Create one under Buildings first.' | t }}</p></div>
        }
      }
    </div>

    <ng-template #markIcon let-status>
      @switch (status) {
        @case ('VERIFIED') {
          <svg viewBox="0 0 16 16" width="14" height="14"><circle cx="8" cy="8" r="7" fill="currentColor" /><path d="M4.6 8.3 7 10.6l4.4-4.6" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
        }
        @case ('DRAFT') {
          <svg viewBox="0 0 16 16" width="14" height="14"><path d="M2.5 11.5 10.8 3.2a1.4 1.4 0 0 1 2 2L4.5 13.5H2.5z" fill="currentColor" /></svg>
        }
        @default {
          <svg viewBox="0 0 16 16" width="14" height="14"><circle cx="8" cy="8" r="6.2" fill="none" stroke="currentColor" stroke-width="1.6" stroke-dasharray="2.4 2" /></svg>
        }
      }
    </ng-template>
  `,
  styles: `
    .legend {
      display: flex;
      flex-wrap: wrap;
      gap: 0.3rem 1.1rem;
      color: var(--text);
    }
    .legend-item {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
    }
    .building {
      display: grid;
      grid-template-columns: 5.5rem minmax(0, 1fr) auto;
      gap: 1rem;
      align-items: center;
    }
    @media (max-width: 560px) {
      .building {
        grid-template-columns: 4rem minmax(0, 1fr);
      }
      .lift {
        grid-column: 1 / -1;
      }
    }
    .thumb {
      width: 100%;
      aspect-ratio: 1;
    }
    .thumb path {
      stroke-width: 1;
      vector-effect: non-scaling-stroke;
    }
    .thumb-none {
      display: grid;
      place-items: center;
      border: 1px dashed var(--border-strong);
      border-radius: var(--radius-sm);
      color: var(--text-muted);
      font-weight: 700;
    }
    .who h2 {
      margin: 0;
      font-size: 1.05rem;
    }
    .facts {
      display: flex;
      flex-wrap: wrap;
      gap: 0.2rem 0.6rem;
      margin-top: 0.2rem;
      color: var(--text-muted);
    }
    .code {
      padding: 0 0.35rem;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      background: var(--bg-inset);
      color: var(--text);
      font-weight: 600;
    }
    .lift {
      display: flex;
      flex-direction: column-reverse;
      flex-wrap: wrap-reverse;
      max-height: 15rem;
      gap: 0.3rem;
      align-content: flex-end;
    }
    .stop {
      display: inline-flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.35rem;
      min-width: 4.4rem;
      padding: 0.3rem 0.55rem;
      border: 1px solid var(--border);
      border-radius: 999px;
      background: var(--bg-elevated);
      color: var(--text);
      text-decoration: none;
      font-weight: 700;
      font-size: 0.85rem;
    }
    .stop:hover {
      background: var(--bg-hover);
      border-color: var(--border-strong);
    }
    .mark {
      display: inline-flex;
    }
    .verified .mark,
    .mark.verified {
      color: var(--success);
    }
    .draft .mark,
    .mark.draft {
      color: var(--warning);
    }
    .unmapped .mark,
    .mark.unmapped {
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
  readonly statuses: FloorStatus[] = ['VERIFIED', 'DRAFT', 'UNMAPPED'];

  /**
   * Each building as a row: seen from above, by its real name with its code beside it, where it
   * is, and what people call it - but not again by its own code ("CPC 1" for CPC1), which only
   * repeated the title.
   */
  readonly rows = computed(() =>
    this.buildings().map((building) => {
      const same = (a: string, b: string) => a.replace(/\s+/g, '').toLowerCase() === b.replace(/\s+/g, '').toLowerCase();
      return {
        building,
        thumb: thumbnail(building),
        nicknames: building.aliases.filter((a) => !same(a, building.code) && !same(a, building.name)),
        floors: building.floors,
      };
    }),
  );

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

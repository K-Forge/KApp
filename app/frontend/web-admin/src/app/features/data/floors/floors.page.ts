import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { FLOOR_STATUS_LABELS, floorLabel, type Building, type Floor, type FloorStatus } from '../buildings/building.model';
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

      <div class="skyline">
        @for (b of rows(); track b.building.code) {
          <article class="card bldg">
            <header class="head">
              @if (b.thumb; as thumb) {
                <svg class="thumb" [attr.viewBox]="thumb.viewBox" aria-hidden="true">
                  @for (part of thumb.parts; track $index) {
                    <path [attr.d]="part.d" [attr.fill]="part.fill" [attr.fill-opacity]="part.opacity" [attr.stroke]="part.fill" />
                  }
                </svg>
              } @else {
                <div class="thumb none" aria-hidden="true"></div>
              }
              <div class="who">
                <h2>{{ b.building.name }}</h2>
                <div class="facts small">
                  <span class="code">{{ b.building.code }}</span>
                  @if (b.building.address) {
                    <span>{{ b.building.address }}</span>
                  }
                </div>
              </div>
            </header>
            <p class="aka small">
              @if (b.nicknames.length) {
                {{ 'Known as {names}' | t: { names: b.nicknames.join(', ') } }}
              }
            </p>
            <!-- The building cut through: its floors stacked as they stand, the highest on top,
                 and what is under the street below the line. -->
            <div class="above" role="group" [attr.aria-label]="'Floors of {name}' | t: { name: b.building.name }">
              @for (floor of b.above; track floor.code) {
                <ng-container *ngTemplateOutlet="storey; context: { $implicit: floor, building: b.building.code }" />
              }
            </div>
            <div class="below">
              <div class="street" aria-hidden="true">{{ 'street' | t }}</div>
              @for (floor of b.below; track floor.code) {
                <ng-container *ngTemplateOutlet="storey; context: { $implicit: floor, building: b.building.code }" />
              }
            </div>
          </article>
        } @empty {
          @if (!loading() && !error()) {
            <div class="card empty-state"><p>{{ 'No buildings yet. Create one under Buildings first.' | t }}</p></div>
          }
        }
      </div>
    </div>

    <ng-template #storey let-floor let-building="building">
      <a
        [class]="'storey ' + statusClass(floor.status)"
        [routerLink]="['/data/floors', building, floor.code]"
        [title]="floor.name + ' · ' + (statusOf(floor) | t)"
      >
        <b>{{ floor.code }}</b>
        @if (named(floor); as name) {
          <span class="name">{{ name }}</span>
        }
        <span class="mark" [attr.aria-label]="statusOf(floor) | t">
          <ng-container *ngTemplateOutlet="markIcon; context: { $implicit: floor.status ?? 'UNMAPPED' }" />
        </span>
      </a>
    </ng-template>

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
    }
    .legend-item {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
    }
    /* Several buildings to a row, each standing on the same street line: the cards share their
       rows - heading, names, floors above, street and below - so the streets of a row line up
       whether or not a building has a basement. */
    .skyline {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(13.5rem, 1fr));
      gap: 1rem;
    }
    .bldg {
      display: grid;
      grid-row: span 4;
      grid-template-rows: subgrid;
      row-gap: 0.5rem;
    }
    .head {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .thumb {
      flex: none;
      width: 3.5rem;
      height: 3.5rem;
    }
    .thumb path {
      stroke-width: 1;
      vector-effect: non-scaling-stroke;
    }
    .none {
      border: 1px dashed var(--border-strong);
      border-radius: var(--radius-md);
    }
    .who {
      min-width: 0;
    }
    h2 {
      margin: 0;
      font-size: 1rem;
      line-height: 1.25;
    }
    .facts {
      display: flex;
      flex-wrap: wrap;
      gap: 0.2rem 0.5rem;
      margin-top: 0.25rem;
      color: var(--text-muted);
    }
    .code {
      padding: 0 0.35rem;
      border-radius: var(--radius-sm);
      background: var(--nav-active-bg);
      color: var(--nav-active-text);
      font-weight: 700;
    }
    .aka {
      margin: 0;
      color: var(--text-muted);
    }
    .above,
    .below {
      display: grid;
      align-content: end;
      gap: 3px;
    }
    .below {
      align-content: start;
    }
    .storey {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      min-height: 2.1rem;
      padding: 0 0.7rem;
      border: 1px solid transparent;
      border-radius: 5px;
      color: var(--text);
      text-decoration: none;
      font-size: 0.85rem;
      transition: transform var(--transition-fast), box-shadow var(--transition-fast);
    }
    .storey:first-child {
      border-top-left-radius: 10px;
      border-top-right-radius: 10px;
    }
    .storey:hover {
      transform: translateX(3px);
      box-shadow: var(--shadow-sm);
      border-color: var(--border-strong);
    }
    .name {
      color: var(--text-muted);
    }
    .mark {
      display: inline-flex;
      margin-left: auto;
    }
    .storey.verified {
      background: var(--success-bg);
    }
    .storey.draft {
      background: var(--warning-bg);
    }
    .storey.unmapped {
      border: 1px dashed var(--border-strong);
      background: color-mix(in srgb, var(--bg-elevated) 60%, transparent);
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
    /* The street: a line the floors stand on, the basements hang under. */
    .street {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0.15rem 0;
      color: var(--text-faint);
      font-size: 0.6875rem;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    .street::before,
    .street::after {
      content: '';
      flex: 1;
      border-top: 2px solid var(--border-strong);
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
   * Each building as a card: seen from above, by its real name with its code beside it, where it
   * is, and what people call it - but not again by its own code ("CPC 1" for CPC1), which only
   * repeated the title. Its floors stand as a section of it, the highest on top, the basements
   * under the street.
   */
  readonly rows = computed(() =>
    this.buildings().map((building) => {
      const same = (a: string, b: string) => a.replace(/\s+/g, '').toLowerCase() === b.replace(/\s+/g, '').toLowerCase();
      const floors = [...building.floors].sort((a, b) => b.level - a.level);
      return {
        building,
        thumb: thumbnail(building),
        nicknames: building.aliases.filter((a) => !same(a, building.code) && !same(a, building.name)),
        above: floors.filter((f) => f.level > 0),
        below: floors.filter((f) => f.level <= 0),
      };
    }),
  );

  /** How far along a floor is, in words. */
  statusOf(floor: Floor): string {
    return this.statusLabels[floor.status ?? 'UNMAPPED'];
  }

  /** A floor's own name, when it says more than its code: "Terraza", not "Piso 3" beside P3. */
  named(floor: Floor): string | null {
    const label = floorLabel(floor);
    return label === floor.code ? null : floor.name.trim();
  }

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

import { ChangeDetectionStrategy, Component, ViewChild, inject, signal } from '@angular/core';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { DataTableComponent } from '../../../shared/ui/data-table/data-table.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { BuildingFormComponent } from './building-form.component';
import { FLOOR_STATUS_LABELS, type Building, type BuildingRequest, type FloorStatus } from './building.model';
import { BuildingsService } from './buildings.service';

/** Full CRUD over /api/map/buildings - the smaller of the two map entities, so no server paging. */
@Component({
  selector: 'app-buildings-page',
  imports: [DataTableComponent, ApiErrorBannerComponent, ModalComponent, BuildingFormComponent, PageIntroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        title="Buildings"
        what="The buildings of a campus, the wings they are split into and the floors inside each one - with how far each floor is from being trusted."
        [can]="['Create a building with its wings and floors', 'Edit its names, its wings or a floor', 'Delete one that has no spaces on it', 'Find one by code, name or any other name']"
        note="A floor is known by its code - S1, P0, P1, MEZZ, T - and its level only orders them, so a mezzanine is 1.5. A floor or a wing that still has spaces cannot be removed - the save is refused naming it - and a building that still has spaces cannot be deleted."
      >
        <button actions type="button" class="btn btn-primary" (click)="openCreate()">New building</button>
      </app-page-intro>

      <div class="card stack">
        <div class="row spread">
          <div class="field" style="margin-bottom: 0; flex: 1 1 14rem">
            <label for="q">Search</label>
            <input id="q" type="text" placeholder="code, name or other name" (input)="onQueryInput($event)" />
          </div>
          <div class="field" style="margin-bottom: 0; flex: 1 1 14rem">
            <label for="campus">Campus</label>
            <input id="campus" type="text" placeholder="filter by campus" (input)="onCampusInput($event)" />
          </div>
        </div>

        <app-api-error-banner [error]="error()" />

        <app-data-table [loading]="loading()" [empty]="!loading() && !error() && buildings().length === 0" emptyMessage="No buildings yet.">
          <thead>
            <tr>
              <th>Code</th>
              <th>Name</th>
              <th>Campus</th>
              <th>Wings</th>
              <th>Floors</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (building of buildings(); track building.code) {
              <tr>
                <td class="mono">{{ building.code }}</td>
                <td>
                  {{ building.name }}
                  @if (building.aliases.length) {
                    <div class="text-faint">{{ building.aliases.join(' · ') }}</div>
                  }
                </td>
                <td>{{ building.campus }}</td>
                <td class="text-muted">{{ wingNames(building) }}</td>
                <td style="min-width: 10rem">
                  <div class="row" style="flex-wrap: wrap; gap: 0.25rem">
                    @for (floor of building.floors; track floor.code) {
                      <span [class]="statusBadge(floor.status)" [title]="floor.name + ': ' + statusLabels[floor.status ?? 'UNMAPPED']">
                        {{ floor.code }}
                      </span>
                    }
                  </div>
                </td>
                <td class="row">
                  <button type="button" class="btn btn-sm" (click)="openEdit(building)">Edit</button>
                  <button type="button" class="btn btn-sm btn-danger" [disabled]="deletingCode() === building.code" (click)="remove(building)">
                    Delete
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </app-data-table>
      </div>
    </div>

    <app-modal #formModal [title]="editingBuilding() ? 'Edit building' : 'New building'" (closed)="formError.set(null)">
      <app-api-error-banner [error]="formError()" />
      <app-building-form
        [initial]="editingBuilding()"
        [submitting]="formSubmitting()"
        (submitted)="save($event)"
        (cancelled)="formModal.close()"
      />
    </app-modal>
  `,
})
export class BuildingsPage {
  private readonly buildingsService = inject(BuildingsService);

  readonly loading = signal(false);
  readonly error = signal<ApiError | null>(null);
  readonly buildings = signal<Building[]>([]);

  readonly editingBuilding = signal<Building | null>(null);
  readonly formSubmitting = signal(false);
  readonly formError = signal<ApiError | null>(null);
  readonly deletingCode = signal<string | null>(null);

  readonly statusLabels = FLOOR_STATUS_LABELS;

  @ViewChild('formModal') private formModal?: ModalComponent;

  private campus = '';
  private query = '';
  private debounceHandle?: ReturnType<typeof setTimeout>;

  constructor() {
    this.fetch();
  }

  private fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.buildingsService.list(this.campus.trim() || undefined, this.query.trim() || undefined).subscribe({
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

  onCampusInput(event: Event): void {
    this.campus = (event.target as HTMLInputElement).value;
    this.fetchSoon();
  }

  onQueryInput(event: Event): void {
    this.query = (event.target as HTMLInputElement).value;
    this.fetchSoon();
  }

  private fetchSoon(): void {
    clearTimeout(this.debounceHandle);
    this.debounceHandle = setTimeout(() => this.fetch(), 300);
  }

  wingNames(building: Building): string {
    return building.wings.length ? building.wings.map((w) => w.name).join(', ') : '—';
  }

  /** Green once walked, amber while it only comes from photos, grey while nothing is drawn. */
  statusBadge(status: FloorStatus | undefined): string {
    switch (status) {
      case 'VERIFIED':
        return 'badge badge-success';
      case 'DRAFT':
        return 'badge badge-warning';
      default:
        return 'badge badge-neutral';
    }
  }

  openCreate(): void {
    this.editingBuilding.set(null);
    this.formError.set(null);
    this.formModal?.open();
  }

  openEdit(building: Building): void {
    this.editingBuilding.set(building);
    this.formError.set(null);
    this.formModal?.open();
  }

  save(request: BuildingRequest): void {
    this.formSubmitting.set(true);
    this.formError.set(null);
    const editing = this.editingBuilding();
    const call = editing ? this.buildingsService.update(editing.code, request) : this.buildingsService.create(request);

    call.subscribe({
      next: () => {
        this.formSubmitting.set(false);
        this.formModal?.close();
        this.fetch();
      },
      error: (err: unknown) => {
        this.formSubmitting.set(false);
        this.formError.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }

  remove(building: Building): void {
    if (!window.confirm(`Delete building ${building.code} — ${building.name}? This cannot be undone.`)) {
      return;
    }
    this.deletingCode.set(building.code);
    this.buildingsService.delete(building.code).subscribe({
      next: () => {
        this.deletingCode.set(null);
        this.fetch();
      },
      error: (err: unknown) => {
        this.deletingCode.set(null);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }
}

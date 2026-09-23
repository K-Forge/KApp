import { ChangeDetectionStrategy, Component, ViewChild, computed, effect, inject, signal } from '@angular/core';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import type { PageResponse } from '../../../core/http/page-response.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { DataTableComponent } from '../../../shared/ui/data-table/data-table.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import type { Building } from '../buildings/building.model';
import { BuildingsService } from '../buildings/buildings.service';
import { SpaceFormComponent } from './space-form.component';
import { CATEGORY_LABELS, SPACE_CATEGORIES, shownCode, type Space, type SpaceCategory, type SpaceRequest, type SpaceType } from './space.model';
import { SpaceTypesService } from './space-types.service';
import { SpacesService } from './spaces.service';

const PAGE_SIZE = 20;
const MIN_QUERY_LENGTH = 2;

/** Full-text search plus CRUD over /api/map/spaces - the "find a room" screen turned inside out. */
@Component({
  selector: 'app-spaces-page',
  imports: [DataTableComponent, ApiErrorBannerComponent, ModalComponent, SpaceFormComponent, PageIntroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        title="Spaces"
        what="Every room, office, bathroom, lift and stairwell the map can point at - placed on the grid of its floor, or only inventoried until somebody places it."
        [can]="['Create a space, with or without a place on the grid', 'Edit what it is called, what it is and how to reach it', 'Delete one', 'Search by door number, name or other name', 'List a building, a floor or a category']"
        note="The code shown is the one on the door, and a space whose door has no number shows a dash: its internal code is never shown, because a made-up code on screen looks exactly like a real one. Reached via has to name a lift, stairs or entrance of the same building, or the save is refused."
      >
        <button actions type="button" class="btn btn-primary" (click)="openCreate()">New space</button>
      </app-page-intro>

      <div class="card stack">
        <div class="row spread">
          <div class="field" style="flex: 1 1 14rem; margin-bottom: 0">
            <label for="q">Search</label>
            <input id="q" type="text" placeholder="door number, name or other name (min 2 characters)" (input)="onQueryInput($event)" />
          </div>
          <div class="field" style="margin-bottom: 0">
            <label for="category">Category</label>
            <select id="category" (change)="onCategoryChange($event)">
              <option value="">All categories</option>
              @for (category of categories; track category) {
                <option [value]="category">{{ categoryLabels[category] }}</option>
              }
            </select>
          </div>
          <div class="field" style="margin-bottom: 0">
            <label for="type">Type</label>
            <select id="type" [value]="type()" (change)="onTypeChange($event)">
              <option value="">All types</option>
              @for (option of typeOptions(); track option.code) {
                <option [value]="option.code">{{ option.name }}</option>
              }
            </select>
          </div>
          <div class="field" style="margin-bottom: 0">
            <label for="building">Building</label>
            <select id="building" (change)="onBuildingChange($event)">
              <option value="">All buildings</option>
              @for (building of buildings(); track building.code) {
                <option [value]="building.code">{{ building.code }}</option>
              }
            </select>
          </div>
          <div class="field" style="margin-bottom: 0">
            <label for="floor">Floor</label>
            <select id="floor" [value]="floor()" [disabled]="!floorOptions().length" (change)="onFloorChange($event)">
              <option value="">All floors</option>
              @for (option of floorOptions(); track option.code) {
                <option [value]="option.code">{{ option.code }}</option>
              }
            </select>
          </div>
        </div>

        <app-api-error-banner [error]="error()" />

        @if (nothingAsked()) {
          <div class="empty-state">
            <p>
              Search by door number, name or other name — at least {{ minQueryLength }} characters —
              or pick a building, a category or a type to list what is there.
            </p>
          </div>
        } @else {
          <app-data-table
            [loading]="loading()"
            [empty]="!loading() && !error() && (result()?.content?.length ?? 0) === 0"
            [emptyMessage]="emptyMessage()"
            [totalItems]="result()?.totalElements ?? null"
            [page]="page()"
            [totalPages]="result()?.totalPages ?? 0"
            (pageChange)="onPageChange($event)"
          >
            <thead>
              <tr>
                <th>Door</th>
                <th>Name</th>
                <th>Type</th>
                <th>Building</th>
                <th>Floor</th>
                <th>On the grid</th>
                <th>Capacity</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (space of result()?.content ?? []; track space.id) {
                <tr>
                  <td class="mono">{{ shownCode(space) ?? '—' }}</td>
                  <td>{{ space.name }}</td>
                  <td>
                    {{ space.typeName ?? space.typeCode }}
                    @if (space.category; as category) {
                      <span class="badge badge-neutral">{{ categoryLabels[category] }}</span>
                    }
                  </td>
                  <td>{{ space.buildingCode }}{{ space.wing ? ' · ' + space.wing : '' }}</td>
                  <td>{{ space.floorCode }}</td>
                  <td>
                    @if (space.gridRow != null && space.gridColumn != null) {
                      <span class="text-muted">{{ space.gridRow }}, {{ space.gridColumn }}</span>
                    } @else {
                      <span class="badge badge-warning">Not placed</span>
                    }
                  </td>
                  <td class="text-muted">{{ space.capacity ?? '—' }}</td>
                  <td class="row">
                    <button type="button" class="btn btn-sm" (click)="openEdit(space)">Edit</button>
                    <button type="button" class="btn btn-sm btn-danger" [disabled]="deletingId() === space.id" (click)="remove(space)">
                      Delete
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </app-data-table>
        }
      </div>
    </div>

    <app-modal #formModal [title]="editingSpace() ? 'Edit space' : 'New space'" (closed)="formError.set(null)">
      <app-api-error-banner [error]="formError()" />
      <app-space-form
        [initial]="editingSpace()"
        [buildings]="buildings()"
        [types]="types()"
        [submitting]="formSubmitting()"
        (submitted)="save($event)"
        (cancelled)="formModal.close()"
      />
    </app-modal>
  `,
})
export class SpacesPage {
  private readonly spacesService = inject(SpacesService);
  private readonly buildingsService = inject(BuildingsService);
  private readonly spaceTypesService = inject(SpaceTypesService);

  readonly categories = SPACE_CATEGORIES;
  readonly categoryLabels = CATEGORY_LABELS;
  readonly shownCode = shownCode;
  readonly minQueryLength = MIN_QUERY_LENGTH;

  readonly query = signal('');
  readonly category = signal<SpaceCategory | ''>('');
  readonly type = signal('');
  readonly buildingCodeFilter = signal('');
  readonly floor = signal('');
  readonly page = signal(0);
  /** Bumped after a save/delete to re-run the search effect without changing any real filter. */
  private readonly refreshTick = signal(0);

  readonly loading = signal(false);
  readonly error = signal<ApiError | null>(null);
  readonly result = signal<PageResponse<Space> | null>(null);
  readonly buildings = signal<Building[]>([]);
  readonly types = signal<SpaceType[]>([]);

  /** The types of the chosen category, or all of them. */
  readonly typeOptions = computed(() => {
    const category = this.category();
    return this.types().filter((t) => !category || t.category === category);
  });

  /** A floor filter only means something inside one building. */
  readonly floorOptions = computed(() => this.buildings().find((b) => b.code === this.buildingCodeFilter())?.floors ?? []);

  readonly editingSpace = signal<Space | null>(null);
  readonly formSubmitting = signal(false);
  readonly formError = signal<ApiError | null>(null);
  readonly deletingId = signal<string | null>(null);

  /** Searching and listing fail differently, and saying "no match" to a listing is wrong. */
  readonly emptyMessage = computed(() =>
    this.query().trim().length > 0
      ? 'No spaces match this search.'
      : 'Nothing here yet. Create a space, or widen the filter.',
  );

  /** True while the screen has been given neither a usable term nor a filter to list by. */
  readonly nothingAsked = computed(() => {
    const q = this.query().trim();
    if (q.length >= MIN_QUERY_LENGTH) return false;
    if (q.length > 0) return true;
    return !this.category() && !this.type() && !this.buildingCodeFilter();
  });

  @ViewChild('formModal') private formModal?: ModalComponent;

  private debounceHandle?: ReturnType<typeof setTimeout>;

  private readonly searchEffect = effect(() => {
    const q = this.query().trim();
    const category = this.category();
    const type = this.type();
    const buildingCode = this.buildingCodeFilter();
    const floor = this.floor();
    const page = this.page();
    this.refreshTick();

    // A term shorter than the minimum is a half-typed search, not a request to list the
    // campus - so it waits. A filter with no term at all is a different question entirely:
    // "what is in building A". The server answers that now, and without it this screen could
    // not show you the space you had just created, and the building and type dropdowns did
    // nothing on their own.
    const searching = q.length > 0;
    const filtering = !!category || !!type || !!buildingCode;

    if (searching && q.length < MIN_QUERY_LENGTH) {
      this.result.set(null);
      return;
    }
    if (!searching && !filtering) {
      this.result.set(null);
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.spacesService
      .search({
        q: searching ? q : undefined,
        page,
        size: PAGE_SIZE,
        category: category || undefined,
        type: type || undefined,
        buildingCode: buildingCode || undefined,
        floor: floor || undefined,
      })
      .subscribe({
        next: (page) => {
          this.result.set(page);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          this.error.set(err instanceof AppHttpError ? err.apiError : null);
        },
      });
  });

  constructor() {
    this.buildingsService.list().subscribe({ next: (buildings) => this.buildings.set(buildings) });
    this.spaceTypesService.list().subscribe({ next: (types) => this.types.set(types) });
  }

  onQueryInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    clearTimeout(this.debounceHandle);
    this.debounceHandle = setTimeout(() => {
      this.page.set(0);
      this.query.set(value);
    }, 300);
  }

  onCategoryChange(event: Event): void {
    this.page.set(0);
    this.category.set((event.target as HTMLSelectElement).value as SpaceCategory | '');
    // A type from another category would filter everything out without saying why.
    if (this.type() && !this.typeOptions().some((t) => t.code === this.type())) {
      this.type.set('');
    }
  }

  onTypeChange(event: Event): void {
    this.page.set(0);
    this.type.set((event.target as HTMLSelectElement).value);
  }

  onBuildingChange(event: Event): void {
    this.page.set(0);
    this.buildingCodeFilter.set((event.target as HTMLSelectElement).value);
    this.floor.set('');
  }

  onFloorChange(event: Event): void {
    this.page.set(0);
    this.floor.set((event.target as HTMLSelectElement).value);
  }

  onPageChange(page: number): void {
    this.page.set(page);
  }

  openCreate(): void {
    this.editingSpace.set(null);
    this.formError.set(null);
    this.formModal?.open();
  }

  openEdit(space: Space): void {
    this.editingSpace.set(space);
    this.formError.set(null);
    this.formModal?.open();
  }

  save(request: SpaceRequest): void {
    this.formSubmitting.set(true);
    this.formError.set(null);
    const editing = this.editingSpace();
    const call = editing ? this.spacesService.update(editing.code, editing.buildingCode, request) : this.spacesService.create(request);

    call.subscribe({
      next: () => {
        this.formSubmitting.set(false);
        this.formModal?.close();
        this.rerunSearch();
      },
      error: (err: unknown) => {
        this.formSubmitting.set(false);
        this.formError.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }

  remove(space: Space): void {
    const label = shownCode(space) ? `${shownCode(space)} — ${space.name}` : space.name;
    if (!window.confirm(`Delete ${label} (${space.buildingCode} ${space.floorCode})? This cannot be undone.`)) {
      return;
    }
    this.deletingId.set(space.id);
    this.spacesService.delete(space.code, space.buildingCode).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.rerunSearch();
      },
      error: (err: unknown) => {
        this.deletingId.set(null);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }

  private rerunSearch(): void {
    this.refreshTick.update((n) => n + 1);
  }
}

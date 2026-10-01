import { ChangeDetectionStrategy, Component, ViewChild, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of, switchMap, map } from 'rxjs';
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
import { SpaceTypesPage } from './space-types.page';
import { SpacesService } from './spaces.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { t } from '../../../core/i18n/i18n.service';

const PAGE_SIZE = 20;
const MIN_QUERY_LENGTH = 2;
/** The most the server gives in one page: the whole list comes in as few requests as that allows. */
const FETCH_SIZE = 100;
/** Alphabetical as a person reads it: accents and case aside, Aula 2 before Aula 10. */
const BY_NAME = new Intl.Collator('es', { sensitivity: 'base', numeric: true });

/** Full-text search plus CRUD over /api/map/spaces - the "find a room" screen turned inside out. */
@Component({
  selector: 'app-spaces-page',
  imports: [TranslatePipe, RouterLink, DataTableComponent, ApiErrorBannerComponent, ModalComponent, SpaceFormComponent, PageIntroComponent, SpaceTypesPage],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        [title]="'Spaces' | t"
        [what]="'Every room, office, bathroom, lift and stair the map can show.' | t"
        [can]="[('Create, edit and delete spaces' | t), ('Find one by door number or name' | t), ('Filter by building, floor or category' | t), ('Create, rename and delete the types of space' | t)]"
        [note]="'The code shown is the one on the door; a door with no number shows a dash.' | t"
      >
      </app-page-intro>

      <nav class="tabs" [attr.aria-label]="'Spaces and their types' | t">
        <a routerLink="." [queryParams]="{}" [class.on]="tab() !== 'types'" [attr.aria-current]="tab() !== 'types' ? 'page' : null">{{ 'Spaces' | t }}</a>
        <a routerLink="." [queryParams]="{ tab: 'types' }" [class.on]="tab() === 'types'" [attr.aria-current]="tab() === 'types' ? 'page' : null">{{ 'Types of space' | t }}</a>
      </nav>

      @if (tab() === 'types') {
        <app-space-types-page [embedded]="true" />
      } @else {

      <div class="card stack">
        <div class="work-bar">
          <div class="field" style="flex: 1 1 14rem; margin-bottom: 0">
            <label for="q">{{ 'Search' | t }}</label>
            <input id="q" type="text" [placeholder]="'door number, name or other name (min 2 characters)' | t" (input)="onQueryInput($event)" />
          </div>
          <div class="field" style="margin-bottom: 0">
            <label for="category">{{ 'Category' | t }}</label>
            <select id="category" (change)="onCategoryChange($event)">
              <option value="">{{ 'All categories' | t }}</option>
              @for (category of categories; track category) {
                <option [value]="category">{{ categoryLabels[category] | t }}</option>
              }
            </select>
          </div>
          <div class="field" style="margin-bottom: 0">
            <label for="type">{{ 'Type' | t }}</label>
            <select id="type" [value]="type()" (change)="onTypeChange($event)">
              <option value="">{{ 'All types' | t }}</option>
              @for (option of typeOptions(); track option.code) {
                <option [value]="option.code">{{ option.name }}</option>
              }
            </select>
          </div>
          <div class="field" style="margin-bottom: 0">
            <label for="building">{{ 'Building' | t }}</label>
            <select id="building" (change)="onBuildingChange($event)">
              <option value="">{{ 'All buildings' | t }}</option>
              @for (building of buildings(); track building.code) {
                <option [value]="building.code">{{ building.code }}</option>
              }
            </select>
          </div>
          <div class="field" style="margin-bottom: 0">
            <label for="floor">{{ 'Floor' | t }}</label>
            <select id="floor" [value]="floor()" [disabled]="!floorOptions().length" (change)="onFloorChange($event)">
              <option value="">{{ 'All floors' | t }}</option>
              @for (option of floorOptions(); track option.code) {
                <option [value]="option.code">{{ option.code }}</option>
              }
            </select>
          </div>
          <button type="button" class="btn btn-primary work-create" (click)="openCreate()">{{ 'New space' | t }}</button>
        </div>

        <app-api-error-banner [error]="error()" />

        @if (nothingAsked()) {
          <div class="empty-state">
            <p>
              {{ 'Search by door number, name or other name — at least {minQueryLength} characters — or pick a building, a category or a type to list what is there.' | t: { minQueryLength: minQueryLength } }}
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
                <th>{{ 'Door' | t }}</th>
                <th>{{ 'Name' | t }}</th>
                <th>{{ 'Type' | t }}</th>
                <th>{{ 'Building' | t }}</th>
                <th>{{ 'Floor' | t }}</th>
                <th>{{ 'On the plan' | t }}</th>
                <th>{{ 'Capacity' | t }}</th>
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
                      <span class="badge badge-neutral">{{ categoryLabels[category] | t }}</span>
                    }
                  </td>
                  <td>{{ space.buildingCode }}{{ space.wing ? ' · ' + space.wing : '' }}</td>
                  <td>{{ space.floorCode }}</td>
                  <td>
                    @if (space.shape) {
                      <span class="text-muted">{{ !space.doors?.length ? ('Drawn' | t) : space.doors.length === 1 ? ('Drawn · 1 door' | t) : ('Drawn · {doors} doors' | t: { doors: space.doors.length }) }}</span>
                    } @else {
                      <span class="badge badge-warning">{{ 'Not drawn' | t }}</span>
                    }
                  </td>
                  <td class="text-muted">{{ space.capacity ?? '—' }}</td>
                  <td class="row">
                    <button type="button" class="btn btn-sm" (click)="openEdit(space)">{{ 'Edit' | t }}</button>
                    <button type="button" class="btn btn-sm btn-danger" [disabled]="deletingId() === space.id" (click)="remove(space)">
                      {{ 'Delete' | t }}
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </app-data-table>
        }
      </div>
      }
    </div>

    <app-modal #formModal [title]="editingSpace() ? ('Edit space' | t) : ('New space' | t)" (closed)="formError.set(null)">
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
      ? t('No spaces match this search.')
      : t('Nothing here yet. Create a space, or widen the filter.'),
  );

  /** True while the screen has been given neither a usable term nor a filter to list by. */
  readonly nothingAsked = computed(() => {
    const q = this.query().trim();
    return q.length > 0 && q.length < MIN_QUERY_LENGTH;
  });

  /** `?tab=types` opens the types of space; anything else, the spaces. */
  readonly tab = input<string | undefined>(undefined);

  /** Every space the filters allow, by name - what the screen opens on, with no search typed. */
  private readonly listed = signal<Space[] | null>(null);
  private readonly searched = signal<PageResponse<Space> | null>(null);

  /** The page shown: the server's when searching, by relevance; else a page of the whole list, by name. */
  readonly result = computed<PageResponse<Space> | null>(() => {
    if (this.query().trim().length > 0) return this.searched();
    const all = this.listed();
    if (!all) return null;
    const page = this.page();
    const totalPages = Math.max(1, Math.ceil(all.length / PAGE_SIZE));
    return {
      content: all.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE),
      page,
      size: PAGE_SIZE,
      totalElements: all.length,
      totalPages,
      first: page === 0,
      last: page >= totalPages - 1,
    };
  });

  @ViewChild('formModal') private formModal?: ModalComponent;

  private debounceHandle?: ReturnType<typeof setTimeout>;

  private readonly searchEffect = effect(() => {
    const q = this.query().trim();
    const category = this.category();
    const type = this.type();
    const buildingCode = this.buildingCodeFilter();
    const floor = this.floor();
    this.refreshTick();
    const filters = {
      category: category || undefined,
      type: type || undefined,
      buildingCode: buildingCode || undefined,
      floor: floor || undefined,
    };

    // A term shorter than the minimum is a half-typed search: it waits.
    if (q.length > 0 && q.length < MIN_QUERY_LENGTH) {
      this.searched.set(null);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const failed = (err: unknown) => {
      this.loading.set(false);
      this.error.set(err instanceof AppHttpError ? err.apiError : null);
    };

    if (q.length > 0) {
      this.spacesService.search({ q, page: this.page(), size: PAGE_SIZE, ...filters }).subscribe({
        next: (found) => {
          this.searched.set(found);
          this.loading.set(false);
        },
        error: failed,
      });
      return;
    }
    // No term: every space the filters allow, all of it, so it can be read in alphabetical order.
    this.spacesService
      .search({ page: 0, size: FETCH_SIZE, ...filters })
      .pipe(
        switchMap((first) =>
          first.totalPages <= 1
            ? of([first])
            : forkJoin([of(first), ...Array.from({ length: first.totalPages - 1 }, (_, i) => this.spacesService.search({ page: i + 1, size: FETCH_SIZE, ...filters }))]),
        ),
        map((pages) => pages.flatMap((p) => p.content).sort((a, b) => BY_NAME.compare(a.name, b.name) || BY_NAME.compare(shownCode(a) ?? '', shownCode(b) ?? ''))),
      )
      .subscribe({
        next: (all) => {
          this.listed.set(all);
          this.loading.set(false);
        },
        error: failed,
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
    if (!window.confirm(t('Delete {label} ({building} {floor})? This cannot be undone.', { label, building: space.buildingCode, floor: space.floorCode }))) {
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

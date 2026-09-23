import { ChangeDetectionStrategy, Component, HostListener, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import {
  ACCESSIBILITY,
  ACCESSIBILITY_LABELS,
  FLOOR_STATUSES,
  FLOOR_STATUS_LABELS,
  type Accessibility,
  type Building,
  type Corridor,
  type FloorStatus,
  type GridPoint,
} from '../buildings/building.model';
import { CATEGORY_COLORS, CATEGORY_LABELS, SPACE_CATEGORIES, type Space, type SpaceCategory, type SpaceType } from '../spaces/space.model';
import { SpaceTypesService } from '../spaces/space-types.service';
import { SpacesService } from '../spaces/spaces.service';
import { CorridorsPanelComponent } from './corridors-panel.component';
import {
  addSpaces,
  assignBox,
  fromDetail,
  isPlaced,
  label,
  newBox,
  newKey,
  place,
  problems,
  rangeSpaces,
  rectOf,
  refusePlacement,
  removeSpace,
  sameFloor,
  toRequest,
  toggleCorridorPoint,
  unplace,
  updateSpace,
  type DraftSpace,
  type FloorDraft,
  type PlacementRefusal,
  type RangeRequest,
  type Rect,
} from './floor-draft';
import { clearDraft, loadDraft, storeDraft, type StoredDraft } from './floor-draft.store';
import { FloorGridComponent, type EditorMode } from './floor-grid.component';
import type { FloorDetail } from './floor.model';
import { FloorsService } from './floors.service';
import { InventoryTrayComponent, type OneSpace } from './inventory-tray.component';
import { SpaceInspectorComponent, type CirculationOption } from './space-inspector.component';

type Tab = 'space' | 'inventory' | 'corridors' | 'floor';

const CELL_KEY = 'kapp-admin:floor-editor:cell-size';
const MIN_CELL = 20;
const MAX_CELL = 64;
const HISTORY = 100;

function storedCellSize(): number {
  try {
    const value = Number(localStorage.getItem(CELL_KEY));
    return value >= MIN_CELL && value <= MAX_CELL ? value : 40;
  } catch {
    return 40;
  }
}

/**
 * The floor editor: the one place a floor of the campus map is drawn and corrected, meant to be
 * used standing in that floor with an iPad.
 *
 * <p>The whole floor is one draft - its grid, its spaces placed or not, its corridors - saved in
 * a single request that the server refuses if somebody else saved the floor in the meantime.
 * Between saves the draft lives on this device, so nothing drawn is lost to a dropped connection
 * or a closed tab.
 */
@Component({
  selector: 'app-floor-editor-page',
  imports: [
    RouterLink,
    ApiErrorBannerComponent,
    FloorGridComponent,
    SpaceInspectorComponent,
    InventoryTrayComponent,
    CorridorsPanelComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="editor-page">
      <div class="head">
        <div>
          <a routerLink="/data/floors" class="back">← All floors</a>
          <h1>
            {{ buildingDoc()?.name ?? building() }} · {{ detail()?.name ?? floor() }}
            @if (dirty()) {
              <span class="badge badge-warning">Unsaved</span>
            }
          </h1>
        </div>
        <div class="row head-actions">
          @if (buildingDoc(); as b) {
            <label class="sr-only" for="floor-switch">Floor</label>
            <select id="floor-switch" class="floor-switch" [value]="floor()" (change)="switchFloor($event)">
              @for (f of b.floors; track f.code) {
                <option [value]="f.code" [selected]="f.code === floor()">{{ f.code }} — {{ f.name }}</option>
              }
            </select>
          }
          <button type="button" class="btn btn-sm" [disabled]="!canUndo()" (click)="undo()" aria-label="Undo" title="Undo">↶</button>
          <button type="button" class="btn btn-sm" [disabled]="!canRedo()" (click)="redo()" aria-label="Redo" title="Redo">↷</button>
          <button type="button" class="btn btn-primary" [disabled]="!canSave()" (click)="save()">
            {{ saving() ? 'Saving…' : 'Save floor' }}
          </button>
        </div>
      </div>

      <p class="save-state text-muted" aria-live="polite">{{ saveState() }}</p>

      <app-api-error-banner [error]="loadError()" />

      @if (pendingDraft(); as pending) {
        <div class="card banner warn" role="alert">
          <p><strong>This device has changes to this floor that were never saved</strong>, from {{ time(pending.savedAt) }}.</p>
          @if (pending.baseVersion !== detail()?.version) {
            <p>
              Somebody has saved the floor since they were made. Restoring them and saving will
              replace what that person saved.
            </p>
          }
          <div class="row">
            <button type="button" class="btn btn-sm btn-primary" (click)="restorePending()">Restore my changes</button>
            <button type="button" class="btn btn-sm btn-danger" (click)="discardPending()">Discard them</button>
          </div>
        </div>
      }

      @if (conflict()) {
        <div class="card banner warn" role="alert">
          <p>
            <strong>Somebody saved this floor after you opened it.</strong> Nothing of yours was
            saved, and your changes are still here and on this device.
          </p>
          <div class="row">
            <button type="button" class="btn btn-sm" (click)="takeTheirs()">Load theirs, drop mine</button>
            <button type="button" class="btn btn-sm btn-danger" (click)="keepMine()">Save mine over theirs</button>
          </div>
        </div>
      }

      <app-api-error-banner [error]="saveError()" />

      @if (issues().length) {
        <details class="card banner problems" [open]="issues().length <= 5">
          <summary>
            {{ issues().length }} thing{{ issues().length === 1 ? '' : 's' }} to fix before this floor can be saved
          </summary>
          <ul>
            @for (issue of issues(); track $index) {
              <li>
                @if (issue.key) {
                  <button type="button" class="link" (click)="selectSpace(issue.key)">{{ issue.text }}</button>
                } @else if (issue.corridor !== undefined) {
                  <button type="button" class="link" (click)="tab.set('corridors')">{{ issue.text }}</button>
                } @else {
                  {{ issue.text }}
                }
              </li>
            }
          </ul>
        </details>
      }

      @if (draft(); as d) {
        <div class="workspace">
          <section class="card canvas" aria-label="Floor">
            <div class="toolbar">
              <div class="segmented" role="radiogroup" aria-label="What a touch on the grid does">
                <button type="button" role="radio" [attr.aria-checked]="mode() === 'select'" [class.on]="mode() === 'select'" (click)="setMode('select')">
                  Select
                </button>
                <button type="button" role="radio" [attr.aria-checked]="mode() === 'box'" [class.on]="mode() === 'box'" (click)="setMode('box')">
                  Draw boxes
                </button>
                <button type="button" role="radio" [attr.aria-checked]="mode() === 'corridor'" [class.on]="mode() === 'corridor'" (click)="setMode('corridor')">
                  Corridor
                </button>
              </div>
              <div class="row zoom">
                <button type="button" class="btn btn-sm" aria-label="Smaller cells" [disabled]="cellSize() <= minCell" (click)="zoom(-4)">−</button>
                <button type="button" class="btn btn-sm" aria-label="Bigger cells" [disabled]="cellSize() >= maxCell" (click)="zoom(4)">+</button>
              </div>
            </div>
            <p class="hint-line" aria-live="polite">
              @if (notice()) {
                <strong>{{ notice() }}</strong>
              }
              {{ hint() }}
            </p>
            <div class="scroller">
              <app-floor-grid
                [rows]="d.gridRows"
                [columns]="d.gridColumns"
                [cellSize]="cellSize()"
                [spaces]="d.spaces"
                [corridors]="d.corridors"
                [categories]="categories()"
                [selectedKey]="selectedKey()"
                [problemKeys]="problemKeys()"
                [activeCorridor]="activeCorridor()"
                [mode]="mode()"
                [disabled]="!!pendingDraft() || saving()"
                [canDraw]="canDraw"
                (cellTap)="onCellTap($event)"
                (boxTap)="onBoxTap($event)"
                (rectDrawn)="onRectDrawn($event)"
              />
            </div>
            <ul class="legend" aria-label="Colours">
              @for (category of legend; track category) {
                <li><span class="swatch" [style.background]="colors[category]"></span>{{ categoryLabels[category] }}</li>
              }
            </ul>
          </section>

          <aside class="card side" [class.locked]="!!pendingDraft()">
            <div class="tabs" role="tablist">
              <button type="button" role="tab" [attr.aria-selected]="tab() === 'space'" [class.on]="tab() === 'space'" (click)="tab.set('space')">
                Space
              </button>
              <button type="button" role="tab" [attr.aria-selected]="tab() === 'inventory'" [class.on]="tab() === 'inventory'" (click)="tab.set('inventory')">
                Inventory <span class="count">{{ unplaced().length }}</span>
              </button>
              <button type="button" role="tab" [attr.aria-selected]="tab() === 'corridors'" [class.on]="tab() === 'corridors'" (click)="tab.set('corridors')">
                Corridors <span class="count">{{ d.corridors.length }}</span>
              </button>
              <button type="button" role="tab" [attr.aria-selected]="tab() === 'floor'" [class.on]="tab() === 'floor'" (click)="tab.set('floor')">
                Floor
              </button>
            </div>

            <div class="panel" role="tabpanel">
              @switch (tab()) {
                @case ('space') {
                  @if (selected(); as space) {
                    <app-space-inspector
                      [space]="space"
                      [types]="types()"
                      [wings]="buildingDoc()?.wings ?? []"
                      [circulation]="circulation()"
                      [unplaced]="unplaced()"
                      [floorAccessibility]="d.accessibility"
                      [issues]="selectedIssues()"
                      [placing]="placingKey() === space.key"
                      (patch)="patchSelected($event)"
                      (nudge)="nudge($event.rows, $event.cols)"
                      (resize)="resize($event.rows, $event.cols)"
                      (move)="togglePlacing(space.key)"
                      (unplace)="unplaceSelected()"
                      (remove)="removeSelected()"
                      (assign)="assign($event)"
                    />
                  } @else {
                    <p class="text-muted">
                      Tap a box on the grid to edit it, or pick a space from the inventory to place it.
                    </p>
                  }
                }
                @case ('inventory') {
                  <app-inventory-tray
                    [spaces]="unplaced()"
                    [types]="types()"
                    [wings]="buildingDoc()?.wings ?? []"
                    [selectedKey]="placingKey()"
                    (pick)="pickFromInventory($event)"
                    (addOne)="addOne($event)"
                    (addRange)="addRange($event)"
                  />
                }
                @case ('corridors') {
                  <app-corridors-panel
                    [corridors]="d.corridors"
                    [active]="activeCorridor()"
                    (activate)="activateCorridor($event)"
                    (remove)="removeCorridor($event)"
                    (changed)="changeCorridor($event.index, $event.patch)"
                    (create)="createCorridor($event)"
                  />
                }
                @case ('floor') {
                  <div class="stack floor-settings">
                    <div class="row spread">
                      <div class="field" style="flex: 1 1 9rem">
                        <label for="f-status">Status</label>
                        <select id="f-status" (change)="setFloor({ status: $any($event.target).value })">
                          @for (status of statuses; track status) {
                            <option [value]="status" [selected]="status === d.status">{{ statusLabels[status] }}</option>
                          }
                        </select>
                      </div>
                      <div class="field" style="flex: 1 1 9rem">
                        <label for="f-access">Reachable without stairs?</label>
                        <select id="f-access" (change)="setFloor({ accessibility: $any($event.target).value })">
                          @for (value of accessibility; track value) {
                            <option [value]="value" [selected]="value === d.accessibility">{{ accessibilityLabels[value] }}</option>
                          }
                        </select>
                      </div>
                    </div>
                    <div class="field">
                      <label for="f-note">How to get here</label>
                      <input id="f-note" type="text" [value]="d.note" placeholder="Se sube por la escalera exterior" (change)="setFloor({ note: $any($event.target).value })" />
                    </div>
                    <div class="row spread">
                      <div class="field" style="flex: 1 1 6rem">
                        <label for="f-rows">Rows</label>
                        <input id="f-rows" type="number" min="1" max="60" [value]="d.gridRows" (change)="setGrid('gridRows', $event)" />
                      </div>
                      <div class="field" style="flex: 1 1 6rem">
                        <label for="f-cols">Columns</label>
                        <input id="f-cols" type="number" min="1" max="60" [value]="d.gridColumns" (change)="setGrid('gridColumns', $event)" />
                      </div>
                    </div>
                    <p class="text-faint small">
                      Shrinking the grid keeps every space; the ones that no longer fit are listed
                      to fix before saving. The floor's code, name and level are edited under
                      Buildings.
                    </p>
                  </div>
                }
              }
            </div>
          </aside>
        </div>
      } @else if (loading()) {
        <div class="card empty-state"><p>Loading the floor…</p></div>
      }
    </div>
  `,
  styles: `
    .editor-page {
      container-type: inline-size;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      align-items: flex-end;
      justify-content: space-between;
    }
    .head h1 {
      margin: 0.25rem 0 0;
      display: flex;
      gap: 0.5rem;
      align-items: center;
      flex-wrap: wrap;
    }
    .back {
      font-size: 0.875rem;
      color: var(--text-muted);
    }
    .head-actions {
      gap: 0.5rem;
      flex-wrap: wrap;
    }
    .head-actions .btn {
      min-height: 2.5rem;
      min-width: 2.5rem;
    }
    .floor-switch {
      width: auto;
      min-height: 2.5rem;
    }
    .save-state {
      margin: 0;
      font-size: 0.8125rem;
    }
    .banner {
      padding: 0.75rem 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .banner p {
      margin: 0;
    }
    .banner.warn {
      border-color: var(--warning);
      background: var(--warning-bg);
    }
    .banner.problems {
      border-color: var(--danger);
    }
    .banner.problems summary {
      cursor: pointer;
      font-weight: 600;
      color: var(--danger-strong);
    }
    .banner.problems ul {
      margin: 0.5rem 0 0;
      padding-left: 1.1rem;
    }
    .link {
      background: none;
      border: 0;
      padding: 0.125rem 0;
      color: inherit;
      text-align: left;
      text-decoration: underline;
      text-underline-offset: 2px;
    }
    .workspace {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(17rem, 22rem);
      gap: 0.75rem;
      align-items: start;
    }
    @container (max-width: 760px) {
      .workspace {
        grid-template-columns: minmax(0, 1fr);
      }
    }
    .canvas,
    .side {
      padding: 0.75rem;
      min-width: 0;
    }
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      justify-content: space-between;
      align-items: center;
    }
    .segmented {
      display: inline-flex;
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-sm);
      overflow: hidden;
    }
    .segmented button {
      border: 0;
      background: var(--bg-elevated);
      color: var(--text);
      padding: 0 0.875rem;
      min-height: 2.5rem;
      font-weight: 500;
    }
    .segmented button + button {
      border-left: 1px solid var(--border-strong);
    }
    .segmented button.on {
      background: var(--primary);
      color: var(--text-on-accent);
    }
    .zoom .btn {
      min-width: 2.5rem;
      min-height: 2.5rem;
      font-size: 1.125rem;
    }
    .hint-line {
      margin: 0.5rem 0;
      font-size: 0.8125rem;
      color: var(--text-muted);
      min-height: 1.25rem;
    }
    .scroller {
      overflow: auto;
      max-height: 70vh;
      border-radius: var(--radius-sm);
      -webkit-overflow-scrolling: touch;
    }
    .legend {
      list-style: none;
      margin: 0.5rem 0 0;
      padding: 0;
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem 0.75rem;
      font-size: 0.75rem;
      color: var(--text-muted);
    }
    .legend .swatch {
      display: inline-block;
      width: 0.75rem;
      height: 0.75rem;
      border-radius: 2px;
      margin-right: 0.25rem;
      vertical-align: -1px;
      border: 1px solid rgb(28 33 40 / 30%);
    }
    .side.locked {
      pointer-events: none;
      opacity: 0.5;
    }
    .tabs {
      display: flex;
      gap: 0.25rem;
      border-bottom: 1px solid var(--border);
      margin-bottom: 0.75rem;
      overflow-x: auto;
    }
    .tabs button {
      border: 0;
      background: none;
      color: var(--text-muted);
      padding: 0.5rem 0.625rem;
      min-height: 2.5rem;
      border-bottom: 2px solid transparent;
      white-space: nowrap;
      font-weight: 500;
    }
    .tabs button.on {
      color: var(--text);
      border-bottom-color: var(--primary);
    }
    .count {
      font-size: 0.75rem;
      color: var(--text-faint);
    }
    .floor-settings .field {
      margin-bottom: 0;
    }
    .small {
      font-size: 0.8125rem;
      margin: 0;
    }
  `,
})
export class FloorEditorPage {
  /** Route parameters, bound by the router. */
  readonly building = input.required<string>();
  readonly floor = input.required<string>();

  private readonly floors = inject(FloorsService);
  private readonly spaceTypes = inject(SpaceTypesService);
  private readonly spacesService = inject(SpacesService);
  private readonly router = inject(Router);

  readonly minCell = MIN_CELL;
  readonly maxCell = MAX_CELL;
  readonly statuses = FLOOR_STATUSES;
  readonly statusLabels = FLOOR_STATUS_LABELS;
  readonly accessibility = ACCESSIBILITY;
  readonly accessibilityLabels = ACCESSIBILITY_LABELS;
  readonly legend = SPACE_CATEGORIES;
  readonly colors = CATEGORY_COLORS;
  readonly categoryLabels = CATEGORY_LABELS;

  readonly loading = signal(true);
  readonly loadError = signal<ApiError | null>(null);
  readonly buildingDoc = signal<Building | null>(null);
  /** The floor as the server last returned it. */
  readonly detail = signal<FloorDetail | null>(null);
  readonly draft = signal<FloorDraft | null>(null);
  readonly types = signal<SpaceType[]>([]);
  readonly circulationElsewhere = signal<Space[]>([]);

  readonly saving = signal(false);
  readonly saveError = signal<ApiError | null>(null);
  readonly conflict = signal(false);
  readonly pendingDraft = signal<StoredDraft | null>(null);
  readonly keptAt = signal<string | null>(null);
  readonly keepFailed = signal(false);
  readonly savedAt = signal<string | null>(null);

  readonly mode = signal<EditorMode>('select');
  readonly tab = signal<Tab>('inventory');
  readonly selectedKey = signal<string | null>(null);
  readonly placingKey = signal<string | null>(null);
  readonly activeCorridor = signal<number | null>(null);
  readonly notice = signal('');
  readonly cellSize = signal(storedCellSize());

  private past: FloorDraft[] = [];
  private future: FloorDraft[] = [];
  private readonly historyTick = signal(0);

  readonly serverDraft = computed(() => {
    const detail = this.detail();
    return detail ? fromDetail(detail) : null;
  });

  readonly dirty = computed(() => {
    const draft = this.draft();
    const server = this.serverDraft();
    return !!draft && !!server && !sameFloor(draft, server);
  });

  readonly categories = computed(() => new Map<string, SpaceCategory>(this.types().map((t) => [t.code, t.category])));

  readonly issues = computed(() => {
    const draft = this.draft();
    if (!draft || !this.types().length) return [];
    return problems(draft, {
      categories: this.categories(),
      wings: new Set((this.buildingDoc()?.wings ?? []).map((w) => w.code)),
      circulationElsewhere: new Set(this.circulationElsewhere().map((s) => s.code)),
    });
  });

  readonly problemKeys = computed(() => new Set(this.issues().flatMap((p) => (p.key ? [p.key] : []))));

  readonly selected = computed(() => this.draft()?.spaces.find((s) => s.key === this.selectedKey()) ?? null);
  readonly selectedIssues = computed(() => this.issues().filter((p) => p.key === this.selectedKey()).map((p) => p.text));

  readonly unplaced = computed(() =>
    (this.draft()?.spaces ?? [])
      .filter((s) => !isPlaced(s))
      .sort((a, b) => label(a).localeCompare(label(b), 'es', { numeric: true })),
  );

  /** What "reached via" may point at: circulation drawn on this floor, and on the others. */
  readonly circulation = computed<CirculationOption[]>(() => {
    const here = (this.draft()?.spaces ?? [])
      .filter((s) => this.categories().get(s.typeCode) === 'CIRCULATION' && s.code.trim())
      .map((s) => ({ code: s.code.trim(), name: s.name, floorCode: this.floor() }));
    const elsewhere = this.circulationElsewhere()
      .filter((s) => s.floorCode !== this.floor())
      .map((s) => ({ code: s.code, name: s.name, floorCode: s.floorCode }));
    return [...here, ...elsewhere];
  });

  readonly canUndo = computed(() => (this.historyTick(), this.past.length > 0 && !this.pendingDraft()));
  readonly canRedo = computed(() => (this.historyTick(), this.future.length > 0 && !this.pendingDraft()));
  readonly canSave = computed(
    () => this.dirty() && !this.saving() && !this.issues().length && !this.pendingDraft() && !this.conflict(),
  );

  readonly saveState = computed(() => {
    if (this.saving()) return 'Saving…';
    if (this.dirty()) {
      if (this.keepFailed()) return 'Not saved - and this browser cannot keep a draft, so save before leaving.';
      const at = this.keptAt();
      return at ? `Not saved yet. Kept on this device at ${this.time(at)}.` : 'Not saved yet.';
    }
    const saved = this.savedAt();
    const version = this.detail()?.version;
    return saved ? `Saved at ${this.time(saved)} · version ${version}.` : version !== undefined ? `Up to date · version ${version}.` : '';
  });

  readonly hint = computed(() => {
    if (this.pendingDraft()) return 'Decide first what to do with the changes kept on this device.';
    const placing = this.placingKey();
    const space = placing ? this.draft()?.spaces.find((s) => s.key === placing) : null;
    if (space) return `Tap the cell where ${label(space)} goes - its top-left corner.`;
    switch (this.mode()) {
      case 'box':
        return 'Drag across empty cells to outline a room from the evacuation plan; a single tap draws one cell.';
      case 'corridor': {
        const index = this.activeCorridor();
        const corridor = index !== null ? this.draft()?.corridors[index] : null;
        return corridor
          ? `Drawing ${corridor.name}: tap the cells it runs through in walking order; tap one again to take it out.`
          : 'Pick or create a corridor under Corridors to draw it.';
      }
      default:
        return 'Tap a box to edit it. Use Draw boxes to outline rooms, then say which space each one is.';
    }
  });

  /** Passed to the grid so a box being drawn shows red before it is let go. */
  readonly canDraw = (rect: Rect): boolean => {
    const draft = this.draft();
    return !!draft && refusePlacement(draft, null, rect) === null;
  };

  constructor() {
    effect(() => {
      const building = this.building();
      const floor = this.floor();
      untracked(() => this.load(building, floor));
    });

    // Every change is kept on the device until it is saved; a floor back in step with the
    // server leaves nothing behind.
    effect(() => {
      const draft = this.draft();
      const detail = this.detail();
      const dirty = this.dirty();
      if (!draft || !detail || this.pendingDraft()) return;
      untracked(() => {
        if (dirty) {
          const kept = storeDraft(this.building(), this.floor(), detail.version, draft);
          this.keepFailed.set(!kept);
          this.keptAt.set(kept ? new Date().toISOString() : null);
        } else {
          clearDraft(this.building(), this.floor());
          this.keptAt.set(null);
          this.keepFailed.set(false);
        }
      });
    });
  }

  // ── Loading ────────────────────────────────────────────────────────────────────────────

  private load(buildingCode: string, floorCode: string): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.saveError.set(null);
    this.conflict.set(false);
    this.pendingDraft.set(null);
    this.draft.set(null);
    this.detail.set(null);
    this.selectedKey.set(null);
    this.placingKey.set(null);
    this.activeCorridor.set(null);
    this.mode.set('select');
    this.notice.set('');
    this.savedAt.set(null);
    this.past = [];
    this.future = [];
    this.historyTick.update((n) => n + 1);

    forkJoin({
      building: this.floors.building(buildingCode),
      detail: this.floors.get(buildingCode, floorCode),
      types: this.spaceTypes.list(),
      // Only feeds the "reached via" choices; the floor is editable without it.
      circulation: this.spacesService
        .search({ page: 0, size: 100, buildingCode, category: 'CIRCULATION' })
        .pipe(catchError(() => of({ content: [] as Space[] }))),
    }).subscribe({
      next: ({ building, detail, types, circulation }) => {
        this.buildingDoc.set(building);
        this.types.set(types);
        this.circulationElsewhere.set(circulation.content);
        this.detail.set(detail);
        const server = fromDetail(detail);
        const stored = loadDraft(buildingCode, floorCode);
        if (stored && !sameFloor(stored.draft, server)) {
          this.pendingDraft.set(stored);
        } else if (stored) {
          clearDraft(buildingCode, floorCode);
        }
        this.draft.set(server);
        this.tab.set(server.spaces.some((s) => !isPlaced(s)) ? 'inventory' : 'space');
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.loadError.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }

  restorePending(): void {
    const pending = this.pendingDraft();
    if (!pending) return;
    this.pendingDraft.set(null);
    this.apply(pending.draft);
    this.notice.set('Your changes are back. Save when they are ready.');
  }

  discardPending(): void {
    clearDraft(this.building(), this.floor());
    this.pendingDraft.set(null);
    this.notice.set('The changes kept on this device were discarded.');
  }

  // ── History ────────────────────────────────────────────────────────────────────────────

  private apply(next: FloorDraft): void {
    const current = this.draft();
    if (!current || next === current) return;
    this.past.push(current);
    if (this.past.length > HISTORY) this.past.shift();
    this.future = [];
    this.draft.set(next);
    this.historyTick.update((n) => n + 1);
  }

  undo(): void {
    const previous = this.past.pop();
    const current = this.draft();
    if (!previous || !current) return;
    this.future.push(current);
    this.draft.set(previous);
    this.afterHistory();
  }

  redo(): void {
    const next = this.future.pop();
    const current = this.draft();
    if (!next || !current) return;
    this.past.push(current);
    this.draft.set(next);
    this.afterHistory();
  }

  private afterHistory(): void {
    this.historyTick.update((n) => n + 1);
    const spaces = this.draft()?.spaces ?? [];
    if (!spaces.some((s) => s.key === this.selectedKey())) this.selectedKey.set(null);
    if (!spaces.some((s) => s.key === this.placingKey())) this.placingKey.set(null);
    const corridors = this.draft()?.corridors.length ?? 0;
    const active = this.activeCorridor();
    if (active !== null && active >= corridors) this.activeCorridor.set(null);
    this.notice.set('');
  }

  // ── The grid ───────────────────────────────────────────────────────────────────────────

  setMode(mode: EditorMode): void {
    this.mode.set(mode);
    this.placingKey.set(null);
    this.notice.set('');
    if (mode === 'corridor') {
      this.tab.set('corridors');
      const count = this.draft()?.corridors.length ?? 0;
      if (this.activeCorridor() === null && count === 1) this.activeCorridor.set(0);
    } else {
      this.activeCorridor.set(null);
    }
  }

  zoom(step: number): void {
    const size = Math.max(MIN_CELL, Math.min(MAX_CELL, this.cellSize() + step));
    this.cellSize.set(size);
    try {
      localStorage.setItem(CELL_KEY, String(size));
    } catch {
      // A preference, not data: losing it costs a tap.
    }
  }

  onCellTap(cell: GridPoint): void {
    const draft = this.draft();
    if (!draft) return;
    if (this.mode() === 'corridor') {
      const index = this.activeCorridor();
      if (index === null) {
        this.notice.set('Pick a corridor to draw first.');
        this.tab.set('corridors');
        return;
      }
      this.apply(toggleCorridorPoint(draft, index, cell));
      return;
    }
    const placing = this.placingKey();
    if (placing) {
      this.placeAt(placing, cell);
      return;
    }
    this.selectedKey.set(null);
    this.notice.set('');
  }

  onBoxTap(key: string): void {
    const placing = this.placingKey();
    if (placing && placing !== key) {
      const other = this.draft()?.spaces.find((s) => s.key === key);
      this.notice.set(`That cell is taken by ${other ? label(other) : 'another space'}.`);
      return;
    }
    this.placingKey.set(null);
    this.selectSpace(key);
  }

  onRectDrawn(rect: Rect): void {
    const draft = this.draft();
    if (!draft) return;
    const box = newBox(draft, this.floor(), rect, this.takenElsewhere());
    this.apply(addSpaces(draft, [box]));
    this.selectedKey.set(box.key);
    this.tab.set('space');
    this.notice.set(
      this.unplaced().length
        ? 'Box drawn. Say which inventoried space it is, or describe it.'
        : 'Box drawn. Describe it, or keep drawing.',
    );
  }

  private placeAt(key: string, cell: GridPoint): void {
    const draft = this.draft();
    const space = draft?.spaces.find((s) => s.key === key);
    if (!draft || !space) return;
    const rect = { row: cell.row, col: cell.col, rowSpan: space.rowSpan, colSpan: space.colSpan };
    const refusal = refusePlacement(draft, key, rect);
    if (refusal) {
      this.notice.set(this.refusalText(space, refusal));
      return;
    }
    this.apply(place(draft, key, rect));
    this.placingKey.set(null);
    this.selectedKey.set(key);
    this.notice.set(`${label(space)} placed.`);
  }

  private refusalText(space: DraftSpace, refusal: PlacementRefusal): string {
    return refusal.reason === 'bounds'
      ? `${label(space)} does not fit there - it is ${space.rowSpan} x ${space.colSpan}.`
      : `${label(space)} would share a cell with ${label(refusal.other)}.`;
  }

  /** Codes of spaces on other floors, so a new box never takes one. */
  private takenElsewhere(): Set<string> {
    return new Set(this.circulationElsewhere().filter((s) => s.floorCode !== this.floor()).map((s) => s.code));
  }

  // ── The selected space ─────────────────────────────────────────────────────────────────

  selectSpace(key: string): void {
    this.selectedKey.set(key);
    this.tab.set('space');
  }

  patchSelected(patch: Partial<DraftSpace>): void {
    const draft = this.draft();
    const space = this.selected();
    if (!draft || !space) return;
    let next = updateSpace(draft, space.key, patch);
    // A renamed lift or staircase takes the spaces reached through it along with it.
    const renamed = patch.code !== undefined && patch.code !== space.code ? patch.code : null;
    if (renamed) {
      next = {
        ...next,
        spaces: next.spaces.map((s) => (s.accessVia === space.code ? { ...s, accessVia: renamed } : s)),
      };
    }
    this.apply(next);
  }

  nudge(rows: number, cols: number): void {
    this.reshape((rect) => ({ ...rect, row: rect.row + rows, col: rect.col + cols }));
  }

  resize(rows: number, cols: number): void {
    this.reshape((rect) => ({
      ...rect,
      rowSpan: Math.max(1, rect.rowSpan + rows),
      colSpan: Math.max(1, rect.colSpan + cols),
    }));
  }

  private reshape(change: (rect: Rect) => Rect): void {
    const draft = this.draft();
    const space = this.selected();
    const rect = space ? rectOf(space) : null;
    if (!draft || !space || !rect) return;
    const next = change(rect);
    if (next.rowSpan === rect.rowSpan && next.colSpan === rect.colSpan && next.row === rect.row && next.col === rect.col) return;
    const refusal = refusePlacement(draft, space.key, next);
    if (refusal) {
      this.notice.set(refusal.reason === 'bounds' ? 'That would go past the edge of the grid.' : this.refusalText(space, refusal));
      return;
    }
    this.notice.set('');
    this.apply(place(draft, space.key, next));
  }

  togglePlacing(key: string): void {
    this.placingKey.set(this.placingKey() === key ? null : key);
    this.mode.set('select');
    this.activeCorridor.set(null);
  }

  unplaceSelected(): void {
    const draft = this.draft();
    const space = this.selected();
    if (!draft || !space) return;
    this.apply(unplace(draft, space.key));
    this.placingKey.set(null);
    this.notice.set(`${label(space)} is back in the inventory.`);
  }

  removeSelected(): void {
    const draft = this.draft();
    const space = this.selected();
    if (!draft || !space) return;
    this.apply(removeSpace(draft, space.key));
    this.selectedKey.set(null);
    this.placingKey.set(null);
    this.notice.set(`${label(space)} deleted. Undo brings it back.`);
  }

  assign(spaceKey: string): void {
    const draft = this.draft();
    const box = this.selected();
    const space = draft?.spaces.find((s) => s.key === spaceKey);
    if (!draft || !box || !space) return;
    this.apply(assignBox(draft, box.key, spaceKey));
    this.selectedKey.set(spaceKey);
    this.notice.set(`That box is ${label(space)}.`);
  }

  // ── Inventory ──────────────────────────────────────────────────────────────────────────

  pickFromInventory(key: string): void {
    const same = this.placingKey() === key;
    this.placingKey.set(same ? null : key);
    this.selectedKey.set(same ? null : key);
    this.mode.set('select');
    this.activeCorridor.set(null);
    this.notice.set('');
  }

  addOne(one: OneSpace): void {
    const draft = this.draft();
    if (!draft) return;
    const door = one.doorCode.trim();
    if (door && draft.spaces.some((s) => (s.doorCode ?? '').toUpperCase() === door.toUpperCase())) {
      this.notice.set(`Door ${door} is already on this floor.`);
      return;
    }
    const taken = this.takenElsewhere();
    const codeFree = door && /^[A-Za-z0-9][A-Za-z0-9-]*$/.test(door) && door.length <= 20
      && !draft.spaces.some((s) => s.code.toUpperCase() === door.toUpperCase()) && !taken.has(door);
    const space: DraftSpace = {
      key: newKey(),
      code: codeFree ? door : newBox(draft, this.floor(), { row: 0, col: 0, rowSpan: 1, colSpan: 1 }, taken).code,
      doorCode: door || null,
      wing: one.wing,
      name: one.name,
      typeCode: one.typeCode,
      aliases: [],
      gridRow: null,
      gridColumn: null,
      rowSpan: 1,
      colSpan: 1,
      accessVia: null,
      accessibility: null,
      note: null,
      capacity: null,
    };
    this.apply(addSpaces(draft, [space]));
    this.notice.set(`${label(space)} added to the inventory.`);
  }

  addRange(range: RangeRequest): void {
    const draft = this.draft();
    if (!draft) return;
    const created = rangeSpaces(draft, range);
    const asked = Math.abs(range.to - range.from) + 1;
    if (!created.length) {
      this.notice.set('All of those are already on this floor.');
      return;
    }
    this.apply(addSpaces(draft, created));
    const skipped = asked - created.length;
    this.notice.set(
      `${created.length} added to the inventory${skipped ? `; ${skipped} were already on this floor` : ''}.`,
    );
  }

  // ── Corridors ──────────────────────────────────────────────────────────────────────────

  activateCorridor(index: number | null): void {
    this.activeCorridor.set(index);
    this.mode.set(index === null ? 'select' : 'corridor');
    this.placingKey.set(null);
  }

  removeCorridor(index: number): void {
    const draft = this.draft();
    if (!draft) return;
    const corridor = draft.corridors[index];
    this.apply({ ...draft, corridors: draft.corridors.filter((_, i) => i !== index) });
    const active = this.activeCorridor();
    if (active === index) this.activateCorridor(null);
    else if (active !== null && active > index) this.activeCorridor.set(active - 1);
    this.notice.set(`${corridor.name} deleted. Undo brings it back.`);
  }

  changeCorridor(index: number, patch: Partial<Corridor>): void {
    const draft = this.draft();
    if (!draft) return;
    this.apply({ ...draft, corridors: draft.corridors.map((c, i) => (i === index ? { ...c, ...patch } : c)) });
  }

  createCorridor(corridor: Corridor): void {
    const draft = this.draft();
    if (!draft) return;
    if (draft.corridors.some((c) => c.code.toUpperCase() === corridor.code.toUpperCase())) {
      this.notice.set(`There is already a corridor ${corridor.code} on this floor.`);
      return;
    }
    this.apply({ ...draft, corridors: [...draft.corridors, corridor] });
    this.activateCorridor(draft.corridors.length);
  }

  // ── The floor itself ───────────────────────────────────────────────────────────────────

  setFloor(patch: { status?: FloorStatus; accessibility?: Accessibility; note?: string }): void {
    const draft = this.draft();
    if (!draft) return;
    this.apply({ ...draft, ...patch });
  }

  setGrid(field: 'gridRows' | 'gridColumns', event: Event): void {
    const draft = this.draft();
    const input = event.target as HTMLInputElement;
    if (!draft) return;
    const value = Math.max(1, Math.min(60, Math.round(Number(input.value) || draft[field])));
    input.value = String(value);
    if (value !== draft[field]) this.apply({ ...draft, [field]: value });
  }

  switchFloor(event: Event): void {
    const code = (event.target as HTMLSelectElement).value;
    void this.router.navigate(['/data/floors', this.building(), code]).then((moved) => {
      // A refused navigation leaves the select showing a floor that is not the one open.
      if (!moved) (event.target as HTMLSelectElement).value = this.floor();
    });
  }

  // ── Saving ─────────────────────────────────────────────────────────────────────────────

  save(): void {
    const draft = this.draft();
    const detail = this.detail();
    if (!draft || !detail || !this.canSave()) return;
    this.saving.set(true);
    this.saveError.set(null);
    this.notice.set('');
    const selectedCode = this.selected()?.code;
    this.floors.saveLayout(this.building(), this.floor(), toRequest(draft, detail.version)).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.detail.set(saved);
        const next = fromDetail(saved);
        this.draft.set(next);
        this.savedAt.set(new Date().toISOString());
        // Keys are rebuilt from the saved codes; the selection follows its code across.
        const kept = next.spaces.find((s) => s.code === selectedCode?.trim());
        this.selectedKey.set(kept?.key ?? null);
        this.placingKey.set(null);
        this.past = [];
        this.future = [];
        this.historyTick.update((n) => n + 1);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        const error = err instanceof AppHttpError ? err.apiError : null;
        if (error?.status === 409 && error.details?.some((d) => d.field === 'version')) {
          this.conflict.set(true);
        } else {
          this.saveError.set(error ? this.readable(error, draft) : null);
        }
      },
    });
  }

  /** Loads what somebody else saved and drops what this device had. */
  takeTheirs(): void {
    this.floors.get(this.building(), this.floor()).subscribe({
      next: (latest) => {
        this.conflict.set(false);
        this.detail.set(latest);
        this.apply(fromDetail(latest));
        this.selectedKey.set(null);
        this.notice.set('Loaded what was saved. Undo brings your changes back.');
      },
      error: (err: unknown) => this.saveError.set(err instanceof AppHttpError ? err.apiError : null),
    });
  }

  /** Saves this device's floor over the one somebody else saved, knowingly. */
  keepMine(): void {
    this.floors.get(this.building(), this.floor()).subscribe({
      next: (latest) => {
        this.detail.set(latest);
        this.conflict.set(false);
        this.save();
      },
      error: (err: unknown) => this.saveError.set(err instanceof AppHttpError ? err.apiError : null),
    });
  }

  /** The server names spaces by their position in the request; this names them as people do. */
  private readable(error: ApiError, draft: FloorDraft): ApiError {
    const details = error.details?.map((detail) => {
      const match = /^(spaces|corridors)\[(\d+)\]\.?(.*)$/.exec(detail.field ?? '');
      if (!match) return detail;
      const [, list, index, field] = match;
      const item = list === 'spaces' ? draft.spaces[Number(index)] : draft.corridors[Number(index)];
      const name = !item ? detail.field : list === 'spaces' ? label(item as DraftSpace) : (item as Corridor).name;
      return { ...detail, field: field ? `${name} · ${field}` : name };
    });
    return { ...error, details };
  }

  // ── Leaving ────────────────────────────────────────────────────────────────────────────

  /** Asked by the route before another screen replaces this one. */
  canLeave(): boolean {
    return (
      !this.dirty() ||
      window.confirm(
        this.keepFailed()
          ? 'This floor has changes that are not saved, and this browser could not keep them. Leave and lose them?'
          : 'This floor has changes that are not saved. They stay on this device for next time. Leave anyway?',
      )
    );
  }

  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.dirty()) event.preventDefault();
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    // Typing in a field keeps its own undo; the target is not always an element (the document).
    const target = event.target;
    if (target instanceof Element && target.closest('input, textarea, select')) return;
    const mod = event.metaKey || event.ctrlKey;
    if (mod && event.key.toLowerCase() === 'z') {
      event.preventDefault();
      if (event.shiftKey) this.redo();
      else this.undo();
    } else if (event.key === 'Escape') {
      this.placingKey.set(null);
      this.selectedKey.set(null);
      this.notice.set('');
    }
  }

  time(iso: string): string {
    return new Date(iso).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit', hour12: false });
  }
}

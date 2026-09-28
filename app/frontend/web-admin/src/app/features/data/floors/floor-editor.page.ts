import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
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
  type Compass,
  type Corridor,
  type FloorStatus,
  type Point,
} from '../buildings/building.model';
import { type Space, type SpaceCategory, type SpaceType } from '../spaces/space.model';
import { SpaceTypesService } from '../spaces/space-types.service';
import { SpacesService } from '../spaces/spaces.service';
import { CorridorsPanelComponent } from './corridors-panel.component';
import {
  COMPASS,
  MAX_SIZE,
  addDoor,
  addSpaces,
  assignBox,
  doorAt,
  doorNear,
  doorWidth,
  fingerprint,
  fromDetail,
  isPlaced,
  label,
  move,
  newBox,
  newKey,
  northAngle,
  place,
  problems,
  rangeSpaces,
  rectangle,
  refusePlacement,
  removeDoor,
  removeSpace,
  split,
  sameFloor,
  toRequest,
  toggleCorridorPoint,
  turn,
  turnUp,
  unplace,
  updateSpace,
  withoutVertex,
  type DraftSpace,
  type FloorDraft,
  type PlacementRefusal,
  type RangeRequest,
} from './floor-draft';
import { clearDraft, loadDraft, storeDraft, type StoredDraft } from './floor-draft.store';
import type { Ground } from '../ground/ground.model';
import { GroundService } from '../ground/ground.service';
import { atStreet, groundToDrawing, margins, placementForFloor, surroundings, type Box } from '../ground/ground';
import { FloorLegendComponent } from './floor-legend.component';
import { FloorPlanComponent, type EditorMode } from './floor-plan.component';
import type { FloorDetail } from './floor.model';
import { FloorsService } from './floors.service';
import { InventoryTrayComponent, type OneSpace } from './inventory-tray.component';
import { SpaceInspectorComponent, type CirculationOption } from './space-inspector.component';

type Tab = 'space' | 'inventory' | 'corridors' | 'floor';

const HISTORY = 100;
/** Each zoom step is this much closer; the plan starts fitted to the width it has. */
const ZOOM_STEP = 1.25;
const MIN_ZOOM = -4;
const MAX_ZOOM = 8;

/**
 * The floor editor: the one place a floor of the campus map is drawn and corrected, meant to be
 * used standing in that floor with an iPad.
 *
 * <p>The whole floor is one draft - its drawing, its spaces drawn or not, their doors, its
 * corridors - saved in a single request that the server refuses if somebody else saved the floor
 * in the meantime.
 * Between saves the draft lives on this device, so nothing drawn is lost to a dropped connection
 * or a closed tab.
 */
@Component({
  selector: 'app-floor-editor-page',
  imports: [
    RouterLink,
    ApiErrorBannerComponent,
    FloorPlanComponent,
    SpaceInspectorComponent,
    InventoryTrayComponent,
    CorridorsPanelComponent,
    FloorLegendComponent,
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
            <span class="floor-switch">
              <select id="floor-switch" [value]="floor()" (change)="switchFloor($event)">
                @for (f of b.floors; track f.code) {
                  <option [value]="f.code" [selected]="f.code === floor()">{{ f.code }} — {{ f.name }}</option>
                }
              </select>
              <svg class="chevron" viewBox="0 0 12 12" width="12" height="12" aria-hidden="true">
                <path d="M2.5 4.5 6 8l3.5-3.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </span>
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
          @if (pendingOutdated()) {
            <p>
              <strong>They were made on an older drawing of this floor.</strong> The floor has been
              redrawn since; restoring them brings the old drawing back over the new one.
            </p>
            <div class="row">
              <button type="button" class="btn btn-sm btn-primary" (click)="discardPending()">Discard them</button>
              <button type="button" class="btn btn-sm btn-danger" (click)="restorePending()">Restore the old drawing</button>
            </div>
          } @else {
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
          }
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
              <div class="segmented" role="radiogroup" aria-label="What a touch on the plan does">
                <button type="button" role="radio" [attr.aria-checked]="mode() === 'select'" [class.on]="mode() === 'select'" (click)="setMode('select')">
                  Select
                </button>
                <button type="button" role="radio" [attr.aria-checked]="mode() === 'box'" [class.on]="mode() === 'box'" (click)="setMode('box')">
                  Draw rooms
                </button>
                <button type="button" role="radio" [attr.aria-checked]="mode() === 'corridor'" [class.on]="mode() === 'corridor'" (click)="setMode('corridor')">
                  Corridor
                </button>
              </div>
              <div class="row zoom">
                <button type="button" class="btn btn-sm turn" aria-label="Turn the plan a quarter to the left" title="Turn the plan a quarter to the left" (click)="turnDrawing(-1)">
                  <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
                    <path d="M4.5 12a7.5 7.5 0 1 0 2.2-5.3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
                    <path d="M3.5 3.5v4.5h4.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                </button>
                @if (d.top) {
                  <button type="button" class="compass" [disabled]="d.top === 'NORTH'" (click)="putUp('NORTH')"
                          [attr.aria-label]="'North is ' + northWords(d.top) + (d.top === 'NORTH' ? '' : ' - turn the plan north up')"
                          [title]="'North is ' + northWords(d.top) + (d.top === 'NORTH' ? '' : ' - tap to turn the plan north up')">
                    <svg viewBox="0 0 32 32" width="32" height="32" aria-hidden="true">
                      <g [attr.transform]="'rotate(' + north() + ' 16 16)'">
                        <path d="M16 9 L20.5 21 L16 18 L11.5 21 Z" fill="currentColor" />
                      </g>
                      <!-- The letter stays upright, beyond the needle's tip. -->
                      <text [attr.x]="northLetter(north()).x" [attr.y]="northLetter(north()).y" text-anchor="middle" dominant-baseline="central" font-size="8" font-weight="700" fill="currentColor">N</text>
                    </svg>
                  </button>
                }
                <button type="button" class="btn btn-sm turn" aria-label="Turn the plan a quarter to the right" title="Turn the plan a quarter to the right" (click)="turnDrawing(1)">
                  <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
                    <path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
                    <path d="M20.5 3.5v4.5h-4.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                </button>
                <button type="button" class="btn btn-sm" aria-label="Zoom out" [disabled]="zoomSteps() <= minZoom" (click)="zoom(-1)">−</button>
                <button type="button" class="btn btn-sm" aria-label="Fit to the screen" (click)="zoomSteps.set(0)">Fit</button>
                <button type="button" class="btn btn-sm" aria-label="Zoom in" [disabled]="zoomSteps() >= maxZoom" (click)="zoom(1)">+</button>
              </div>
              <!-- Their own group, so on a phone they wrap onto a line together instead of one of
                   them running off the edge of the card. -->
              @if (floorPlacement() && (groundData() || hasFootprint())) {
                <div class="row layers">
                  @if (hasFootprint()) {
                    <button type="button" class="btn btn-sm layer" [class.on]="showMargin()" [attr.aria-pressed]="showMargin()"
                            title="Where this floor's rooms go, wing by wing, and the floor below's, dotted" (click)="toggleMargin()">
                      <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                        <path d="M3 5h8v14H3zM11 8h10v11H11z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
                      </svg>
                      Margin
                    </button>
                  }
                  @if (groundData()) {
                    <button type="button" class="btn btn-sm layer" [class.on]="showGround()" [attr.aria-pressed]="showGround()"
                            title="The block, the sidewalks and the streets around the building" (click)="toggleGround()">
                      <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                        <path d="M4 21 9 3M20 21 15 3M12 5v2M12 11v2M12 17v2" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
                      </svg>
                      Streets
                    </button>
                  }
                  @if (groundData() && showGround() && hasFootprint()) {
                    <button type="button" class="btn btn-sm layer" [class.on]="showCadastre()" [attr.aria-pressed]="showCadastre()"
                            title="The building's outline as the cadastre records it, the pink lines" (click)="toggleCadastre()">
                      <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                        <path d="M4 4h10v6h6v10H4z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" stroke-dasharray="3 2.5" />
                      </svg>
                      Cadastre
                    </button>
                  }
                </div>
              }
            </div>
            <p class="hint-line" aria-live="polite">
              @if (notice()) {
                <strong>{{ notice() }}</strong>
              }
              {{ hint() }}
            </p>
            <div class="scroller" #scroller>
              <app-floor-plan
                [width]="d.width"
                [height]="d.height"
                [scale]="scale()"
                [spaces]="d.spaces"
                [outline]="d.outline"
                [corridors]="d.corridors"
                [categories]="categories()"
                [selectedKey]="selectedKey()"
                [problemKeys]="problemKeys()"
                [activeCorridor]="activeCorridor()"
                [mode]="canvasMode()"
                [disabled]="!!pendingDraft() || saving()"
                [canPlace]="canPlace"
                [view]="view()"
                [surroundings]="around()"
                [margins]="marginsShown()"
                [streetWalks]="streetWalks()"
                (pointTap)="onPointTap($event)"
                (spaceTap)="onSpaceTap($event)"
                (boxDrawn)="onBoxDrawn($event)"
                (moved)="onMoved($event.key, $event.dx, $event.dy)"
                (reshaped)="onReshaped($event.key, $event.shape)"
                (vertexRemoved)="onVertexRemoved($event.key, $event.index)"
              />
            </div>
            <app-floor-legend [spaces]="d.spaces" [categories]="categories()" [groundSource]="around() ? groundData()?.source ?? null : null"
                              [cadastre]="!!around()?.footprint?.length" [margin]="!!marginsShown()" />
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
                      [doorMode]="mode() === 'door'"
                      [splitMode]="mode() === 'split'"
                      [step]="step()"
                      (patch)="patchSelected($event)"
                      (nudge)="nudge($event.dx, $event.dy)"
                      (move)="togglePlacing(space.key)"
                      (doors)="toggleDoorMode()"
                      (removeDoor)="removeSelectedDoor($event)"
                      (split)="toggleSplitMode()"
                      (unplace)="unplaceSelected()"
                      (remove)="removeSelected()"
                      (assign)="assign($event)"
                    />
                  } @else {
                    <p class="text-muted">
                      Tap a room on the plan to edit it, or pick a space from the inventory to draw it.
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
                        <label for="f-width">Drawing width</label>
                        <input id="f-width" type="number" min="1" [max]="maxSize" [value]="d.width" (change)="setSize('width', $event)" />
                      </div>
                      <div class="field" style="flex: 1 1 6rem">
                        <label for="f-height">Drawing height</label>
                        <input id="f-height" type="number" min="1" [max]="maxSize" [value]="d.height" (change)="setSize('height', $event)" />
                      </div>
                    </div>
                    <div class="field">
                      <span class="label" id="f-top-label">The top of the drawing faces</span>
                      <div class="segmented" role="radiogroup" aria-labelledby="f-top-label">
                        @for (direction of compass; track direction) {
                          <button type="button" role="radio" [attr.aria-checked]="d.top === direction" [class.on]="d.top === direction" (click)="setTop(direction)">
                            {{ compassLabels[direction] }}
                          </button>
                        }
                      </div>
                    </div>
                    <p class="text-faint small">
                      Which way the top of the plan on the wall faces - a compass on the spot
                      settles it. The round arrows next to Fit turn the plan a quarter at a time,
                      moving every room, door and corridor with it and keeping this right; the
                      compass there turns it north up.
                    </p>
                    <p class="text-faint small">
                      In the drawing's own units: a floor traced from its evacuation plan is drawn
                      at the plan's scale. Shrinking it keeps every room; the ones left outside are
                      listed to fix before saving. The floor's code, name and level are edited under
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
    /* The floor switch is a button of the header row, not a form field: each browser drew its own
       arrow at its own height and size - on the iPhone a tall pill unlike every button beside it. */
    .floor-switch {
      position: relative;
      display: inline-flex;
      align-items: center;
    }
    .floor-switch select {
      appearance: none;
      -webkit-appearance: none;
      width: auto;
      min-height: 2.5rem;
      padding: 0 2rem 0 0.75rem;
      border: 1px solid transparent;
      border-radius: var(--radius-sm);
      background: var(--bg-inset);
      color: var(--text);
      font-weight: 600;
      font-size: 0.8125rem;
      line-height: 1.2;
      cursor: pointer;
    }
    .floor-switch select:hover {
      background: var(--bg-hover);
    }
    .floor-switch select:focus-visible {
      outline: 2px solid var(--primary);
      outline-offset: 1px;
    }
    .floor-switch .chevron {
      position: absolute;
      right: 0.625rem;
      pointer-events: none;
      color: var(--text-muted);
    }
    /* Under 16px iOS zooms the whole page when the list opens. */
    @media (max-width: 640px) {
      .floor-switch select {
        font-size: 16px;
      }
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
    .zoom,
    .layers {
      flex-wrap: wrap;
    }
    .layer {
      gap: 0.35rem;
    }
    .layer.on {
      background: var(--primary-bg);
      color: var(--primary);
      border-color: color-mix(in srgb, var(--primary) 35%, transparent);
    }
    .compass {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 2.5rem;
      min-height: 2.5rem;
      padding: 0;
      border: 0;
      background: none;
      color: var(--primary);
      cursor: pointer;
    }
    .compass:disabled {
      cursor: default;
    }
    .zoom .btn,
    .layers .btn {
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
      /* The plan is fitted to this width. Were the scrollbar to take its room only when the plan
         outgrows 70vh, a plan just that tall would shrink, lose the scrollbar, grow back and gain
         it again, every frame: the plan shook. */
      scrollbar-gutter: stable;
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
  private readonly ground = inject(GroundService);

  readonly minZoom = MIN_ZOOM;
  readonly maxZoom = MAX_ZOOM;
  readonly maxSize = MAX_SIZE;
  readonly compass = COMPASS;
  readonly compassLabels: Record<Compass, string> = { NORTH: 'North', EAST: 'East', SOUTH: 'South', WEST: 'West' };
  readonly statuses = FLOOR_STATUSES;
  readonly statusLabels = FLOOR_STATUS_LABELS;
  readonly accessibility = ACCESSIBILITY;
  readonly accessibilityLabels = ACCESSIBILITY_LABELS;

  readonly loading = signal(true);
  readonly loadError = signal<ApiError | null>(null);
  readonly buildingDoc = signal<Building | null>(null);
  /** The city around the building's campus, when the building is laid on the ground. */
  readonly groundData = signal<Ground | null>(null);
  readonly showGround = signal(readShown(SHOW_GROUND_KEY));
  /**
   * Whether the cadastre's parts are drawn over the streets. Off unless asked for: they are what
   * the map is fitted to, and the margin says what they mean for the floor.
   */
  readonly showCadastre = signal(readShown(SHOW_CADASTRE_KEY, false));
  /** Whether the building's margin is drawn: where this floor's rooms go, wing by wing. */
  readonly showMargin = signal(readShown(SHOW_MARGIN_KEY));
  /** The floor as the server last returned it. */
  readonly detail = signal<FloorDetail | null>(null);
  readonly draft = signal<FloorDraft | null>(null);
  readonly types = signal<SpaceType[]>([]);
  readonly circulationElsewhere = signal<Space[]>([]);

  readonly saving = signal(false);
  readonly saveError = signal<ApiError | null>(null);
  readonly conflict = signal(false);
  readonly pendingDraft = signal<StoredDraft | null>(null);
  /** The kept changes started from another drawing than the server's now - or cannot tell. */
  readonly pendingOutdated = computed(() => {
    const pending = this.pendingDraft();
    const server = this.serverDraft();
    return !!pending && !!server && pending.base !== fingerprint(server);
  });
  readonly keptAt = signal<string | null>(null);
  readonly keepFailed = signal(false);
  readonly savedAt = signal<string | null>(null);

  readonly mode = signal<EditorMode>('select');
  readonly tab = signal<Tab>('inventory');
  readonly selectedKey = signal<string | null>(null);
  readonly placingKey = signal<string | null>(null);
  readonly activeCorridor = signal<number | null>(null);
  readonly notice = signal('');
  readonly zoomSteps = signal(0);
  /** The width the plan has on screen, measured, so it starts fitted to it. */
  private readonly available = signal(0);
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

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

  /** Where this floor's drawing lies on the ground, if the building is laid on it. */
  readonly floorPlacement = computed(() => {
    const placement = this.buildingDoc()?.placement;
    const draft = this.draft();
    return placement && draft ? placementForFloor(placement, draft.top, draft.width, draft.height) : null;
  });

  /** North, in degrees clockwise from the drawing's top: exact when the building is on the ground. */
  readonly north = computed(() => {
    const placement = this.floorPlacement();
    return placement ? (360 - placement.bearing) % 360 : (northAngle(this.draft()?.top) ?? 0);
  });

  /** The plane shown: the drawing, and around it enough ground to take in its streets. */
  readonly view = computed<Box | null>(() => {
    const draft = this.draft();
    const placement = this.floorPlacement();
    if (!draft || !placement || !this.groundData() || !this.showGround()) return null;
    const margin = Math.min(Math.max(draft.width, draft.height) * 0.5, GROUND_MARGIN_METRES / placement.metresPerUnit);
    return { x: -margin, y: -margin, width: draft.width + 2 * margin, height: draft.height + 2 * margin };
  });

  /** The block, sidewalks and streets within the view, in the drawing's units. */
  readonly around = computed(() => {
    const view = this.view();
    const ground = this.groundData();
    const placement = this.floorPlacement();
    return view && ground && placement
      ? surroundings(ground, placement, view, this.showCadastre() ? this.footprint() : [], this.level(), this.levelBelow())
      : null;
  });

  private readonly level = computed(() => this.detail()?.level ?? 1);

  /** The level of the floor under this one: the building's, or one down when it has none listed. */
  private readonly levelBelow = computed(() => {
    const level = this.level();
    const lower = (this.buildingDoc()?.floors ?? []).map((f) => f.level).filter((l) => l < level);
    return lower.length ? Math.max(...lower) : level - 1;
  });

  /** Where the rooms go on this floor, wing by wing, and where they went on the one below. */
  readonly marginsShown = computed(() => {
    const placement = this.floorPlacement();
    const footprint = this.footprint();
    if (!placement || !footprint.length || !this.showMargin()) return null;
    return { current: margins(footprint, placement, this.level()), below: margins(footprint, placement, this.levelBelow()) };
  });

  /** On a floor at the street, the sidewalks along the building: where it stops. */
  readonly streetWalks = computed(() => {
    const ground = this.groundData();
    const placement = this.floorPlacement();
    const draft = this.draft();
    if (!ground || !placement || !draft || !atStreet(this.level())) return [];
    const reach = GROUND_MARGIN_METRES / placement.metresPerUnit;
    return ground.sidewalks
      .map((ring) => ring.map((c) => groundToDrawing(placement, c)))
      .filter((ring) => ring.some((p) => p.x > -reach && p.y > -reach && p.x < draft.width + reach && p.y < draft.height + reach));
  });

  private readonly footprint = computed(() => this.buildingDoc()?.footprint ?? []);
  readonly hasFootprint = computed(() => this.footprint().length > 0);

  /** Screen pixels per unit: the plan fitted to the width it has, then zoomed. */
  readonly scale = computed(() => {
    const width = this.view()?.width ?? this.draft()?.width ?? 1;
    const fit = this.available() > 0 ? (this.available() - 2) / width : 1;
    return Math.max(0.05, fit * ZOOM_STEP ** this.zoomSteps());
  });

  /** One press of a Move button: about a finger's width at the fitted zoom, never under a unit. */
  readonly step = computed(() => Math.max(1, Math.round(4 / this.scale())));

  /** Drawing a room for an inventoried space is drawing, whatever tool was picked. */
  readonly canvasMode = computed<EditorMode>(() => (this.placingKey() ? 'box' : this.mode()));

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
    if (space) return `Drag the outline of ${label(space)} on the plan, or tap where it is for a small square to reshape.`;
    switch (this.mode()) {
      case 'box':
        return 'Drag across the plan to outline a room; a tap draws a small square to reshape by its corners.';
      case 'split': {
        const selected = this.selected();
        return selected
          ? `Tap inside ${label(selected)} where the wall between the two rooms is; it is cut across its longer side.`
          : 'Select a room first.';
      }
      case 'door': {
        const selected = this.selected();
        return selected
          ? `Tap a wall of ${label(selected)} to put a door there; tap a door to take it out.`
          : 'Select a room first.';
      }
      case 'corridor': {
        const index = this.activeCorridor();
        const corridor = index !== null ? this.draft()?.corridors[index] : null;
        return corridor
          ? `Drawing ${corridor.name}: tap the points it runs through in walking order; tap one again to take it out.`
          : 'Pick or create a corridor under Corridors to draw it.';
      }
      default:
        return 'Tap a room to edit it: drag it, or drag its corners. Use Draw rooms to outline new ones.';
    }
  });

  /** Passed to the plan so a room being drawn or reshaped shows red before it is let go. */
  readonly canPlace = (key: string | null, shape: Point[]): boolean => {
    const draft = this.draft();
    return !!draft && refusePlacement(draft, key, shape) === null;
  };

  constructor() {
    // The plan starts fitted to the width it is given, and keeps fitting when that changes -
    // an iPad turned, a phone's address bar hiding.
    effect((onCleanup) => {
      const element = this.scroller()?.nativeElement;
      if (!element) return;
      const measure = () => this.available.set(element.clientWidth);
      measure();
      if (typeof ResizeObserver === 'undefined') return;
      const observer = new ResizeObserver(measure);
      observer.observe(element);
      onCleanup(() => observer.disconnect());
    });

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
          const server = this.serverDraft();
          const kept = storeDraft(this.building(), this.floor(), detail.version, draft, server ? fingerprint(server) : undefined);
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
        this.groundData.set(null);
        if (building.placement) {
          this.ground.forCampus(building.campus).subscribe((ground) => {
            if (this.buildingDoc() === building) this.groundData.set(ground);
          });
        }
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

  zoom(steps: number): void {
    this.zoomSteps.update((z) => Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z + steps)));
  }

  /** A tap on the plan, away from any room's click: a corridor's point, a door, a new room. */
  onPointTap(point: Point): void {
    const draft = this.draft();
    if (!draft) return;
    if (this.mode() === 'corridor' && !this.placingKey()) {
      const index = this.activeCorridor();
      if (index === null) {
        this.notice.set('Pick a corridor to draw first.');
        this.tab.set('corridors');
        return;
      }
      this.apply(toggleCorridorPoint(draft, index, point, 8 / this.scale()));
      return;
    }
    if (this.mode() === 'door' && !this.placingKey()) {
      this.doorTap(draft, point);
      return;
    }
    if (this.mode() === 'split' && !this.placingKey()) {
      this.splitTap(draft, point);
      return;
    }
    if (this.canvasMode() === 'box') {
      this.onBoxDrawn(this.squareAt(draft, point));
      return;
    }
    this.selectedKey.set(null);
    this.notice.set('');
  }

  onSpaceTap(key: string): void {
    this.placingKey.set(null);
    this.selectSpace(key);
  }

  /** A room outlined on the plan: the inventoried space being drawn, or a new box to name. */
  onBoxDrawn(shape: Point[]): void {
    const draft = this.draft();
    if (!draft) return;
    const placing = this.placingKey();
    const space = placing ? draft.spaces.find((s) => s.key === placing) : null;
    if (space) {
      const refusal = refusePlacement(draft, space.key, shape);
      if (refusal) {
        this.notice.set(this.refusalText(space, refusal));
        return;
      }
      this.apply(place(draft, space.key, shape));
      this.placingKey.set(null);
      this.selectedKey.set(space.key);
      this.notice.set(`${label(space)} drawn. Drag its corners to match the plan.`);
      return;
    }
    if (refusePlacement(draft, null, shape)) {
      this.notice.set('That outline would overlap another room or leave the drawing.');
      return;
    }
    const box = newBox(draft, this.floor(), shape, this.takenElsewhere());
    this.apply(addSpaces(draft, [box]));
    this.selectedKey.set(box.key);
    this.tab.set('space');
    this.notice.set(
      this.unplaced().length
        ? 'Room drawn. Say which inventoried space it is, or describe it.'
        : 'Room drawn. Describe it, or keep drawing.',
    );
  }

  onMoved(key: string, dx: number, dy: number): void {
    const draft = this.draft();
    const space = draft?.spaces.find((s) => s.key === key);
    if (!draft || !space || !isPlaced(space) || (dx === 0 && dy === 0)) return;
    const next = move(draft, key, dx, dy);
    const refusal = refusePlacement(draft, key, next.spaces.find((s) => s.key === key)?.shape ?? []);
    if (refusal) {
      this.notice.set(this.refusalText(space, refusal));
      return;
    }
    this.notice.set('');
    this.apply(next);
  }

  onReshaped(key: string, shape: Point[]): void {
    const draft = this.draft();
    const space = draft?.spaces.find((s) => s.key === key);
    if (!draft || !space) return;
    const refusal = refusePlacement(draft, key, shape);
    if (refusal) {
      this.notice.set(this.refusalText(space, refusal));
      return;
    }
    const doors = space.doors.length;
    const next = place(draft, key, shape);
    const lost = doors - (next.spaces.find((s) => s.key === key)?.doors.length ?? doors);
    this.notice.set(lost ? `${lost} door${lost === 1 ? '' : 's'} left the outline and went; undo brings ${lost === 1 ? 'it' : 'them'} back.` : '');
    this.apply(next);
  }

  onVertexRemoved(key: string, index: number): void {
    const draft = this.draft();
    const space = draft?.spaces.find((s) => s.key === key);
    if (!draft || !space || !isPlaced(space)) return;
    if (space.shape.length <= 3) {
      this.notice.set('A room needs at least three corners.');
      return;
    }
    this.onReshaped(key, withoutVertex(space.shape, index));
  }

  private doorTap(draft: FloorDraft, point: Point): void {
    const space = this.selected();
    if (!space || !isPlaced(space)) {
      this.notice.set('Select a drawn room first, then tap its walls.');
      return;
    }
    const reach = 12 / this.scale();
    const existing = doorNear(space, point, reach);
    if (existing !== null) {
      this.apply(removeDoor(draft, space.key, existing));
      this.notice.set('Door taken out.');
      return;
    }
    const door = doorAt(space.shape, point, doorWidth(draft));
    if (!door || Math.hypot(point.x - (door.from.x + door.to.x) / 2, point.y - (door.from.y + door.to.y) / 2) > doorWidth(draft) + reach) {
      this.notice.set(`Tap on a wall of ${label(space)}.`);
      return;
    }
    this.apply(addDoor(draft, space.key, door));
    this.notice.set('Door added. Tap it again to take it out.');
  }

  /** The small square a tap draws, centred on it and kept on the drawing. */
  private squareAt(draft: FloorDraft, point: Point): Point[] {
    const side = Math.max(4, Math.round(Math.min(draft.width, draft.height) / 12));
    const x = Math.max(0, Math.min(draft.width - side, Math.round(point.x - side / 2)));
    const y = Math.max(0, Math.min(draft.height - side, Math.round(point.y - side / 2)));
    return rectangle({ x, y, width: side, height: side });
  }

  private refusalText(space: DraftSpace, refusal: PlacementRefusal): string {
    switch (refusal.reason) {
      case 'bounds':
        return `${label(space)} would reach outside the drawing.`;
      case 'shape':
        return `${label(space)}'s outline would cross itself.`;
      default:
        return `${label(space)} would overlap ${label(refusal.other)}.`;
    }
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

  nudge(dx: number, dy: number): void {
    const space = this.selected();
    if (space) this.onMoved(space.key, dx, dy);
  }

  toggleDoorMode(): void {
    this.mode.set(this.mode() === 'door' ? 'select' : 'door');
    this.placingKey.set(null);
    this.activeCorridor.set(null);
    this.notice.set('');
  }

  toggleSplitMode(): void {
    this.mode.set(this.mode() === 'split' ? 'select' : 'split');
    this.placingKey.set(null);
    this.activeCorridor.set(null);
    this.notice.set('');
  }

  private splitTap(draft: FloorDraft, point: Point): void {
    const space = this.selected();
    if (!space || !isPlaced(space)) {
      this.notice.set('Select a drawn room first, then tap where to cut it.');
      return;
    }
    const result = split(draft, space.key, point, this.floor(), this.takenElsewhere());
    if (!result) {
      this.notice.set(`Tap inside ${label(space)}, away from its edges.`);
      return;
    }
    this.apply(result.draft);
    this.mode.set('select');
    this.notice.set(`${label(space)} cut in two. The other part is a box to name; undo puts it back.`);
  }

  removeSelectedDoor(index: number): void {
    const draft = this.draft();
    const space = this.selected();
    if (!draft || !space) return;
    this.apply(removeDoor(draft, space.key, index));
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
      code: codeFree ? door : newBox(draft, this.floor(), null, taken).code,
      doorCode: door || null,
      wing: one.wing,
      name: one.name,
      typeCode: one.typeCode,
      aliases: [],
      shape: null,
      doors: [],
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

  /** Where north is, in words, for whoever cannot see the compass. */
  northWords(top: Compass | null): string {
    return ({ NORTH: 'up', EAST: 'to the left', SOUTH: 'down', WEST: 'to the right' } as const)[top ?? 'NORTH'];
  }

  toggleGround(): void {
    this.showGround.update((shown) => !shown);
    remember(SHOW_GROUND_KEY, this.showGround());
  }

  toggleMargin(): void {
    this.showMargin.update((shown) => !shown);
    remember(SHOW_MARGIN_KEY, this.showMargin());
  }

  toggleCadastre(): void {
    this.showCadastre.update((shown) => !shown);
    remember(SHOW_CADASTRE_KEY, this.showCadastre());
  }

  /** Where the compass's N goes: past the needle's tip, whichever way it points. */
  northLetter(degrees: number): { x: number; y: number } {
    const radians = (degrees * Math.PI) / 180;
    return { x: 16 + 12.5 * Math.sin(radians), y: 16 - 12.5 * Math.cos(radians) };
  }

  setTop(top: Compass): void {
    const draft = this.draft();
    if (!draft || draft.top === top) return;
    this.apply({ ...draft, top });
  }

  turnDrawing(quarters: number): void {
    const draft = this.draft();
    if (!draft) return;
    this.apply(turn(draft, quarters));
  }

  putUp(direction: Compass): void {
    const draft = this.draft();
    if (!draft?.top || draft.top === direction) return;
    this.apply(turnUp(draft, direction));
  }

  setSize(field: 'width' | 'height', event: Event): void {
    const draft = this.draft();
    const input = event.target as HTMLInputElement;
    if (!draft) return;
    const value = Math.max(1, Math.min(MAX_SIZE, Math.round(Number(input.value) || draft[field])));
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

/** Ground drawn around the plan, in metres: a sidewalk and the roadway beyond it, at least. */
const GROUND_MARGIN_METRES = 16;
const SHOW_GROUND_KEY = 'kapp-admin:floor-ground';
const SHOW_CADASTRE_KEY = 'kapp-admin:floor-cadastre';
const SHOW_MARGIN_KEY = 'kapp-admin:floor-margin';

/** Whether this device shows a layer, or `shown` when it never said. */
function readShown(key: string, shown = true): boolean {
  try {
    const kept = localStorage.getItem(key);
    return kept === null ? shown : kept === 'true';
  } catch {
    return shown;
  }
}

function remember(key: string, shown: boolean): void {
  try {
    localStorage.setItem(key, shown ? 'true' : 'false');
  } catch {
    // Non-fatal: the layer just comes back on the next visit.
  }
}

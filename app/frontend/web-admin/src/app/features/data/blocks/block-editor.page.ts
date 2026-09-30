import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { concat, forkJoin, last, of, switchMap, type Observable } from 'rxjs';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import type { Building, FootprintPart } from '../buildings/building.model';
import { BuildingsService } from '../buildings/buildings.service';
import { areaPath, streetLabel, type Box } from '../ground/ground';
import type { Coordinate, Ground } from '../ground/ground.model';
import { GroundService } from '../ground/ground.service';
import {
  alignment,
  areaOf,
  blockOf,
  closeRing,
  distanceAhead,
  edgesOf,
  fromView,
  middleOf,
  openRing,
  pathOf,
  rectangleAt,
  snapped,
  squared,
  toView,
  translate,
  wallsOf,
  withEdgeMoved,
  withInsertedVertex,
  withVertex,
  withoutVertex,
  type Frame,
  type ViewPoint,
} from './block-geometry';
import type { Structure } from './structure.model';
import { StructuresService } from './structures.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { t } from '../../../core/i18n/i18n.service';

/**
 * One outline the editor lets you reshape: a part of a university building, or something else on
 * the block. Kept in the screen's metres; `moved` says whether its outline has been touched, so
 * that one nobody moved is saved exactly as it was read.
 */
interface EditShape {
  key: string;
  kind: 'part' | 'structure';
  /** The building a part belongs to. */
  building: string | null;
  lot: string | null;
  wing: string | null;
  /** A structure's name. */
  name: string;
  floors: number;
  /** A part's lowest floor above the street: 1, or higher for floors carried out over a portico. */
  lowestFloor: number;
  basements: number;
  points: ViewPoint[];
  /** The outline as it was read, for a shape nobody has moved; null for a new one. */
  original: Coordinate[] | null;
  moved: boolean;
}

type Gesture =
  | { kind: 'vertex'; key: string; index: number }
  | { kind: 'edge'; key: string; index: number }
  | { kind: 'move'; key: string }
  | { kind: 'tap'; key: string | null };

/** Pixels per metre at each zoom step. */
const ZOOMS = [3, 4, 5, 6.5, 8, 10, 13, 16, 20];
const WING_COLORS: Record<string, string> = { N: '#e0564f', C: '#3b82f6', S: '#8b5cf6' };
const MORE_WING_COLORS = ['#0d9488', '#d97706', '#db2777', '#65a30d'];
const BUILDING_COLOR = '#c2185b';
const STRUCTURE_COLOR = '#6b7280';
/** How close, in pixels, a dragged corner has to come to another's line to land on it. */
const SNAP_PX = 8;
/** How far, in pixels, a pointer moves before a press is a drag. */
const TAP_SLOP = 4;
const DOUBLE_TAP_MS = 400;
/** Metres round the block the view keeps. */
const MARGIN = 14;
/** The screen area, in square pixels, a shape needs for its label to fit. */
const LABEL_ROOM_PX = 2500;
/** Metres in front of a wall a sidewalk is looked for: a setback, not the street's far side. */
const SETBACK_REACH = 12;

/**
 * The block editor: every outline on one city block - the parts of the university's buildings and
 * whatever else stands there, a neighbour's building, a heritage house and its garden - on the
 * block's lots and streets, to be reshaped until they are what stands there.
 *
 * <p>The block is turned so that its main building's walls run across and down the screen, as the
 * floor editor shows its drawing: pushing a wall out keeps a rectangle a rectangle, and "Square"
 * makes a slanted outline one. A building's parts are saved into its footprint, which is what the
 * floor editor's margin is drawn from; the rest into the campus's structures.
 */
@Component({
  selector: 'app-block-editor-page',
  imports: [TranslatePipe, RouterLink, ApiErrorBannerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="block-page">
      <div class="head">
        <div>
          <a routerLink="/data/campus" class="back">{{ '← Campus map' | t }}</a>
          <h1>
            {{ 'Block {value}' | t: { value: block() ?? '' } }}
            @if (dirty()) {
              <span class="badge badge-warning">{{ 'Unsaved' | t }}</span>
            }
          </h1>
          <p class="text-muted small">{{ '{campus} · the university’s buildings, part by part, and what else stands on the block' | t: { campus: campus() } }}</p>
        </div>
        <div class="row head-actions">
          @if (blocks().length > 1) {
            <label class="sr-only" for="block-switch">{{ 'Block' | t }}</label>
            <select id="block-switch" class="block-switch" [value]="block()" (change)="switchBlock($event)">
              @for (b of blocks(); track b.code) {
                <option [value]="b.code" [selected]="b.code === block()">{{ b.code }} · {{ b.names }}</option>
              }
            </select>
          }
          <button type="button" class="btn btn-sm" [disabled]="!past().length" (click)="undo()" [attr.aria-label]="'Undo' | t" [title]="'Undo' | t">↶</button>
          <button type="button" class="btn btn-sm" [disabled]="!future().length" (click)="redo()" [attr.aria-label]="'Redo' | t" [title]="'Redo' | t">↷</button>
          <button type="button" class="btn btn-sm" [disabled]="!dirty() || saving()" (click)="discard()">{{ 'Discard' | t }}</button>
          <button type="button" class="btn btn-primary" [disabled]="!dirty() || saving()" (click)="save()">
            {{ saving() ? ('Saving…' | t) : ('Save block' | t) }}
          </button>
        </div>
      </div>

      <p class="save-state text-muted" aria-live="polite">{{ saveState() }}</p>
      <app-api-error-banner [error]="error()" />

      @if (loading()) {
        <div class="card empty-state">{{ 'Loading the block…' | t }}</div>
      } @else if (!view()) {
        <div class="card empty-state">{{ 'No university building stands on block {block}.' | t: { block: block() } }}</div>
      } @else {
        <div class="workspace">
          <section class="card canvas">
            <div class="toolbar">
              <div class="group">
                <button type="button" class="btn btn-sm" (click)="turn(-1)" [attr.aria-label]="'Turn the block a quarter to the left' | t" [title]="'Turn a quarter to the left' | t">↺</button>
                <span class="compass" [title]="'North is {where}' | t: { where: northWords() }">
                  <svg viewBox="0 0 32 32" width="30" height="30" aria-hidden="true">
                    <g [attr.transform]="'rotate(' + northAngle() + ' 16 16)'"><path d="M16 9 L20.5 21 L16 18 L11.5 21 Z" fill="currentColor" /></g>
                  </svg>
                </span>
                <button type="button" class="btn btn-sm" (click)="turn(1)" [attr.aria-label]="'Turn the block a quarter to the right' | t" [title]="'Turn a quarter to the right' | t">↻</button>
              </div>
              <div class="group">
                <button type="button" class="btn btn-sm" [attr.aria-label]="'Zoom out' | t" [disabled]="zoom() === 0" (click)="setZoom(zoom() - 1)">−</button>
                <button type="button" class="btn btn-sm" [attr.aria-label]="'Zoom in' | t" [disabled]="zoom() === zooms.length - 1" (click)="setZoom(zoom() + 1)">+</button>
              </div>
            </div>
            <p class="hint-line">
              @if (selected(); as s) {
                {{ 'Drag a corner, or the square on a wall to push the wall out; double-tap a corner to take it away, a wall’s square to add one.' | t }}
              } @else {
                {{ 'Tap an outline to reshape it.' | t }}
              }
            </p>
            <div class="scroller" #scroller>
              @if (view(); as v) {
                <svg
                  #svg
                  role="application"
                  [attr.aria-label]="('Block ' | t) + block() + (', to reshape its outlines' | t)"
                  [attr.viewBox]="v.box.x + ' ' + v.box.y + ' ' + v.box.width + ' ' + v.box.height"
                  [attr.width]="v.box.width * scale()"
                  [attr.height]="v.box.height * scale()"
                  (pointerdown)="onDown($event)"
                  (pointermove)="onMove($event)"
                  (pointerup)="onUp($event)"
                  (pointercancel)="reset()"
                >
                  <defs>
                    <pattern id="open-ground" width="1.2" height="1.2" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
                      <line x1="0" y1="0" x2="0" y2="1.2" class="hatch" />
                    </pattern>
                  </defs>
                  <rect class="paper" [attr.x]="v.box.x" [attr.y]="v.box.y" [attr.width]="v.box.width" [attr.height]="v.box.height" />
                  @for (d of v.roadways; track $index) {
                    <path class="roadway" [attr.d]="d" />
                  }
                  @for (d of v.medians; track $index) {
                    <path class="median" [attr.d]="d" />
                  }
                  @for (d of v.blocks; track $index) {
                    <path class="city-block" [attr.d]="d" />
                  }
                  @for (lot of v.lots; track lot.code) {
                    <path class="lot" [class.other]="!lot.here" [attr.d]="lot.d" />
                  }
                  @for (s of v.streets; track $index) {
                    <text class="street" [attr.transform]="'translate(' + s.x + ' ' + s.y + ') rotate(' + s.angle + ')'" [attr.font-size]="12 / scale()">{{ s.name }}</text>
                  }
                  @for (d of v.context; track $index) {
                    <path class="context" [attr.d]="d" />
                  }
                  @for (s of drawn(); track s.key) {
                    <path
                      class="shape"
                      [class.selected]="s.key === selectedKey()"
                      [class.structure]="s.kind === 'structure'"
                      [attr.data-key]="s.key"
                      [attr.d]="s.d"
                      [attr.fill]="s.open ? 'url(#open-ground)' : s.color"
                      [attr.fill-opacity]="s.open ? 1 : s.opacity"
                      [attr.stroke]="s.color"
                    />
                  }
                  <!-- Over the outlines: a building ends where the sidewalk starts, and one pushed onto
                       it goes under it, in plain sight. -->
                  @for (d of v.sidewalks; track $index) {
                    <path class="sidewalk" [attr.d]="d" />
                  }
                  @for (lot of v.lots; track lot.code) {
                    @if (lot.here) {
                      <text class="lot-code" [attr.x]="lot.at.x" [attr.y]="lot.at.y" [attr.font-size]="10 / scale()">{{ 'lote {value}' | t: { value: lot.code.slice(9) } }}</text>
                    }
                  }
                  @for (s of drawn(); track s.key) {
                    <text class="label" [attr.x]="s.at.x" [attr.y]="s.at.y" [attr.font-size]="11 / scale()">{{ s.label }}</text>
                  }
                  @if (handles(); as h) {
                    @for (w of h.walls; track $index) {
                      <text class="measure" [attr.transform]="'translate(' + w.at.x + ' ' + w.at.y + ') rotate(' + w.angle + ')'" [attr.font-size]="10 / scale()">{{ w.text }}</text>
                      @if (w.setback; as g) {
                        <line class="setback" [attr.x1]="w.middle.x" [attr.y1]="w.middle.y" [attr.x2]="g.x2" [attr.y2]="g.y2" />
                        <text class="measure setback-text" [attr.transform]="'translate(' + g.at.x + ' ' + g.at.y + ') rotate(' + w.angle + ')'" [attr.font-size]="10 / scale()">{{ g.text }}</text>
                      }
                    }
                    @for (m of h.mids; track $index) {
                      <rect class="mid" [attr.data-edge]="$index" [attr.x]="m.x - 5 / scale()" [attr.y]="m.y - 5 / scale()" [attr.width]="10 / scale()" [attr.height]="10 / scale()" />
                    }
                    @for (c of h.corners; track $index) {
                      <circle class="corner" [attr.data-vertex]="$index" [attr.cx]="c.x" [attr.cy]="c.y" [attr.r]="6 / scale()" />
                    }
                  }
                </svg>
              }
            </div>
            <div class="key">
              @for (w of wingKey(); track w.label) {
                <span class="key-item"><span class="swatch" [style.background]="w.color"></span>{{ w.label }}</span>
              }
              <span class="key-item"><span class="swatch" [style.background]="structureColor"></span>{{ 'Not the university’s' | t }}</span>
              <span class="key-item"><span class="swatch hatched"></span>{{ 'No floor: a plaza, a garden, an open passage' | t }}</span>
              <span class="key-item"><span class="swatch lot-key"></span>{{ 'Lot, from the cadastre' | t }}</span>
              <span class="key-item"><span class="swatch walk-key"></span>{{ 'Sidewalk: a building stops before it' | t }}</span>
            </div>
            <p class="credit">{{ 'Lots, blocks and streets: {value}' | t: { value: ground()?.source } }}</p>
          </section>

          <aside class="card side">
            @if (selected(); as s) {
              <h2 class="h">{{ titleOf(s) }}</h2>
              <p class="text-muted small">
                {{ areaOf(s.points).toFixed(1) }} m²
                @if (s.lot) {
                  {{ '· lot {lot}' | t: { lot: s.lot } }}
                } @else {
                  {{ '· found on site' | t }}
                }
              </p>
              @if (s.kind === 'structure') {
                <label class="field">
                  <span>{{ 'Name' | t }}</span>
                  <input type="text" [value]="s.name" maxlength="120" (change)="setName($event)" />
                </label>
              } @else {
                <label class="field">
                  <span>{{ 'Wing' | t }}</span>
                  <select (change)="setWing($event)">
                    <option value="" [selected]="!s.wing">{{ 'None' | t }}</option>
                    @for (w of wingsOf(s.building); track w.code) {
                      <option [value]="w.code" [selected]="w.code === s.wing">{{ w.name }}</option>
                    }
                  </select>
                </label>
              }
              <div class="pair">
                <label class="field">
                  <span>{{ 'Floors' | t }}</span>
                  <input type="number" min="0" max="200" [value]="s.floors" (change)="setNumber($event, 'floors')" />
                </label>
                <label class="field">
                  <span>{{ 'Basements' | t }}</span>
                  <input type="number" min="0" max="20" [value]="s.basements" (change)="setNumber($event, 'basements')" />
                </label>
                @if (s.kind === 'part') {
                  <label class="field">
                    <span>{{ 'From floor' | t }}</span>
                    <input type="number" min="1" [max]="s.floors || 1" [value]="s.lowestFloor" (change)="setNumber($event, 'lowestFloor')" />
                  </label>
                }
              </div>
              @if (s.floors === 0) {
                <p class="text-muted small">{{ 'No floor above the street: drawn hatched, in no floor’s margin.' | t }}</p>
              } @else if (s.lowestFloor > 1) {
                <p class="text-muted small">{{ 'Carried out over the street from floor {floor}: in the margins from that floor up, not in the ground floor’s.' | t: { floor: s.lowestFloor } }}</p>
              }
              <div class="row">
                <button type="button" class="btn btn-sm" (click)="square()" [title]="'The rectangle round it, square to the screen' | t">{{ 'Square' | t }}</button>
                <button type="button" class="btn btn-sm btn-danger" (click)="remove()">{{ 'Delete' | t }}</button>
              </div>
              <hr />
            }
            <h2 class="h">{{ 'Add' | t }}</h2>
            <div class="row add">
              @for (b of editable(); track b.code) {
                <button type="button" class="btn btn-sm" (click)="addPart(b.code)">{{ '+ Part of {code}' | t: { code: b.code } }}</button>
              }
              <button type="button" class="btn btn-sm" (click)="addStructure()">{{ '+ Something else' | t }}</button>
            </div>
            <h2 class="h">{{ 'On the block' | t }}</h2>
            <ul class="list">
              @for (g of groups(); track g.title) {
                <li>
                  <strong>{{ g.title }}</strong>
                  <ul>
                    @for (s of g.shapes; track s.key) {
                      <li>
                        <button type="button" class="link" [class.on]="s.key === selectedKey()" (click)="select(s.key)">{{ s.text }}</button>
                      </li>
                    }
                  </ul>
                </li>
              }
            </ul>
          </aside>
        </div>
      }
    </div>
  `,
  styles: `
    .block-page {
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
    }
    .back,
    .small {
      font-size: 0.8125rem;
    }
    .small {
      margin: 0.25rem 0 0;
    }
    .head-actions {
      gap: 0.5rem;
      flex-wrap: wrap;
    }
    .block-switch {
      width: auto;
    }
    .save-state {
      margin: 0;
      font-size: 0.8125rem;
      min-height: 1rem;
    }
    .workspace {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(16rem, 21rem);
      gap: 0.75rem;
      align-items: start;
    }
    @media (max-width: 900px) {
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
      justify-content: space-between;
      gap: 0.5rem;
    }
    .group {
      display: flex;
      align-items: center;
      gap: 0.25rem;
    }
    .group .btn {
      min-width: 2.5rem;
      min-height: 2.5rem;
    }
    .compass {
      display: inline-flex;
      color: var(--primary);
    }
    .hint-line {
      margin: 0.5rem 0;
      font-size: 0.8125rem;
      color: var(--text-muted);
    }
    .scroller {
      overflow: auto;
      max-height: 72vh;
      border-radius: var(--radius-sm);
      background: color-mix(in srgb, var(--text) 16%, var(--bg-elevated));
    }
    svg {
      display: block;
      touch-action: none;
      user-select: none;
    }
    svg * {
      vector-effect: non-scaling-stroke;
    }
    .paper {
      fill: color-mix(in srgb, var(--text) 16%, var(--bg-elevated));
    }
    .roadway {
      fill: color-mix(in srgb, var(--text) 16%, var(--bg-elevated));
    }
    .median {
      fill: #b9d7a6;
    }
    .city-block {
      fill: var(--bg-elevated);
      stroke: color-mix(in srgb, var(--text) 40%, transparent);
    }
    .sidewalk {
      fill: color-mix(in srgb, #d8c9ad 70%, var(--bg-elevated));
      fill-opacity: 0.92;
      pointer-events: none;
    }
    .lot {
      fill: none;
      stroke: color-mix(in srgb, var(--text) 55%, transparent);
      stroke-width: 1.2;
      stroke-dasharray: 5 4;
    }
    .lot.other {
      stroke: color-mix(in srgb, var(--text) 22%, transparent);
    }
    .context {
      fill: color-mix(in srgb, var(--text) 12%, transparent);
      stroke: color-mix(in srgb, var(--text) 30%, transparent);
    }
    .shape {
      stroke-width: 1.5;
      cursor: pointer;
    }
    .shape.structure {
      stroke-dasharray: 6 3;
    }
    .shape.selected {
      stroke-width: 3;
      cursor: move;
    }
    .hatch {
      stroke: color-mix(in srgb, var(--text) 45%, transparent);
      stroke-width: 0.35;
      vector-effect: none;
    }
    .street,
    .label,
    .lot-code,
    .measure {
      text-anchor: middle;
      dominant-baseline: central;
      paint-order: stroke;
      pointer-events: none;
    }
    .street {
      fill: color-mix(in srgb, var(--text) 72%, transparent);
      font-weight: 600;
      stroke: color-mix(in srgb, var(--text) 16%, var(--bg-elevated));
      stroke-width: 3;
    }
    .label {
      fill: #1c2128;
      font-weight: 700;
      stroke: rgb(255 255 255 / 85%);
      stroke-width: 3;
    }
    .lot-code {
      fill: color-mix(in srgb, var(--text) 60%, transparent);
      stroke: var(--bg-elevated);
      stroke-width: 3;
    }
    .measure {
      fill: var(--primary);
      font-weight: 600;
      stroke: var(--bg-elevated);
      stroke-width: 3;
    }
    .setback {
      stroke: var(--primary);
      stroke-width: 1.5;
      stroke-dasharray: 4 3;
      pointer-events: none;
    }
    .setback-text {
      font-style: italic;
    }
    .corner {
      fill: #fff;
      stroke: var(--primary);
      stroke-width: 2;
      cursor: grab;
    }
    .mid {
      fill: var(--primary);
      fill-opacity: 0.7;
      stroke: #fff;
      stroke-width: 1.5;
      cursor: move;
    }
    .key {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem 1rem;
      margin-top: 0.6rem;
      font-size: 0.75rem;
      color: var(--text-muted);
    }
    .key-item {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
    }
    .swatch {
      width: 0.9rem;
      height: 0.9rem;
      border-radius: 3px;
      opacity: 0.8;
    }
    .swatch.hatched {
      background: repeating-linear-gradient(45deg, var(--text-muted) 0 1px, transparent 1px 4px);
    }
    .swatch.lot-key {
      border: 1.5px dashed var(--text-muted);
    }
    .swatch.walk-key {
      background: #d8c9ad;
    }
    .credit {
      margin: 0.5rem 0 0;
      font-size: 0.6875rem;
      color: var(--text-faint);
    }
    .h {
      font-size: 1rem;
      margin: 0 0 0.25rem;
    }
    .field {
      display: grid;
      gap: 0.25rem;
      margin: 0.5rem 0;
      font-size: 0.8125rem;
    }
    .pair {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(5.5rem, 1fr));
      gap: 0.5rem;
    }
    .add {
      flex-wrap: wrap;
      gap: 0.35rem;
      margin-bottom: 0.75rem;
    }
    .list,
    .list ul {
      margin: 0;
      padding-left: 1rem;
      font-size: 0.8125rem;
    }
    .list > li {
      margin-bottom: 0.4rem;
    }
    .link {
      background: none;
      border: 0;
      padding: 0.1rem 0;
      color: inherit;
      text-align: left;
      cursor: pointer;
    }
    .link.on {
      font-weight: 700;
      color: var(--primary);
    }
    hr {
      border: 0;
      border-top: 1px solid var(--border);
      margin: 0.75rem 0;
    }
  `,
})
export class BlockEditorPage {
  /** The block's code, from the route: the cadastre's nine digits. Absent, the campus's main block. */
  readonly block = input<string>();

  private readonly buildingsService = inject(BuildingsService);
  private readonly grounds = inject(GroundService);
  private readonly structuresService = inject(StructuresService);
  private readonly router = inject(Router);
  private readonly svg = viewChild<ElementRef<SVGSVGElement>>('svg');
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');
  /** Whether the block has been fitted to the screen since it was opened. */
  private fitted = false;
  private opened: string | undefined;

  readonly zooms = ZOOMS;
  readonly structureColor = STRUCTURE_COLOR;
  readonly areaOf = areaOf;

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<ApiError | null>(null);
  readonly saveState = signal('');
  readonly campus = signal('');
  readonly zoom = signal(4);
  readonly ground = signal<Ground | null>(null);
  readonly buildings = signal<Building[]>([]);
  readonly frame = signal<Frame | null>(null);
  readonly shapes = signal<EditShape[]>([]);
  readonly past = signal<EditShape[][]>([]);
  readonly future = signal<EditShape[][]>([]);
  readonly selectedKey = signal<string | null>(null);
  /** The shape being dragged, as it would be dropped. */
  readonly dragged = signal<{ key: string; points: ViewPoint[] } | null>(null);
  private structuresVersion = 0;
  /** What was read, to tell an edit from the saved state and to put back on Discard. */
  private readonly loaded = signal<EditShape[]>([]);

  constructor() {
    effect(() => {
      const block = this.block();
      untracked(() => this.load(block));
    });
    // Opened at the closest zoom that shows the whole block across the screen - a phone's too.
    effect(() => {
      const box = this.view()?.box;
      const width = this.scroller()?.nativeElement.clientWidth;
      if (!box || !width || this.fitted) return;
      this.fitted = true;
      const fits = ZOOMS.map((z, i) => ({ z, i })).filter(({ z }) => box.width * z <= width);
      untracked(() => this.zoom.set(fits.length ? fits[fits.length - 1].i : 0));
    });
  }

  readonly scale = computed(() => ZOOMS[this.zoom()]);

  /** The campus's buildings standing on this block: theirs are the parts the editor reshapes. */
  readonly editable = computed(() => {
    const block = this.block();
    return this.buildings().filter((b) => (b.footprint ?? []).some((p) => blockOf(p.lot) === block));
  });

  /** Every block of the campus a building stands on, with the buildings on it. */
  readonly blocks = computed(() => {
    const byBlock = new Map<string, Set<string>>();
    for (const b of this.buildings()) {
      for (const p of b.footprint ?? []) {
        const code = blockOf(p.lot);
        if (code) byBlock.set(code, (byBlock.get(code) ?? new Set()).add(b.code));
      }
    }
    return [...byBlock].map(([code, names]) => ({ code, names: [...names].sort().join(', ') })).sort((a, b) => a.code.localeCompare(b.code));
  });

  readonly dirty = computed(() => JSON.stringify(this.shapes().map(essence)) !== JSON.stringify(this.loaded().map(essence)));

  readonly selected = computed(() => this.shapes().find((s) => s.key === this.selectedKey()) ?? null);

  /** The ground, the lots and the rest of the campus, on the screen. */
  readonly view = computed(() => {
    const ground = this.ground();
    const frame = this.frame();
    if (!ground || !frame) return null;
    const block = this.block();
    const at = (c: Coordinate) => toView(frame, c);
    const area = (rings: Coordinate[][]) => rings.map((r) => areaPath(r.map(at)));
    const lots = (ground.lots ?? []).map((lot) => {
      const points = openRing(lot.ring).map(at);
      return { code: lot.code, here: blockOf(lot.code) === block, d: pathOf(points), at: middleOf(points), points };
    });
    const focus = [...lots.filter((l) => l.here).flatMap((l) => l.points), ...this.shapes().filter((s) => s.kind === 'part').flatMap((s) => s.points)];
    if (!focus.length) return null;
    const xs = focus.map((p) => p.x);
    const ys = focus.map((p) => p.y);
    const box: Box = {
      x: Math.min(...xs) - MARGIN,
      y: Math.min(...ys) - MARGIN,
      width: Math.max(...xs) - Math.min(...xs) + 2 * MARGIN,
      height: Math.max(...ys) - Math.min(...ys) + 2 * MARGIN,
    };
    const streets: { name: string; x: number; y: number; angle: number }[] = [];
    const seen = new Set<string>();
    for (const street of ground.streets) {
      const label = streetLabel(street.path.map(at), box, 50 / this.scale());
      if (label && !seen.has(street.name)) {
        seen.add(street.name);
        streets.push({ name: street.name, ...label });
      }
    }
    const editable = new Set(this.editable().map((b) => b.code));
    const context = this.buildings()
      .filter((b) => !editable.has(b.code))
      .flatMap((b) => (b.footprint ?? []).filter((p) => p.floors > 0 || p.basements > 0))
      .map((p) => pathOf(openRing(p.ring).map(at)));
    return {
      box,
      blocks: area(ground.blocks),
      roadways: area(ground.roadways),
      medians: area(ground.medians),
      sidewalks: area(ground.sidewalks),
      sidewalkEdges: ground.sidewalks.flatMap((r) => edgesOf(openRing(r).map(at))),
      lots,
      streets,
      context,
    };
  });

  /**
   * The shapes as drawn: coloured by wing, hatched where they rise no floor, and labelled where
   * the label has room - zoomed out, the small parts' names would only pile up.
   */
  readonly drawn = computed(() => {
    const dragged = this.dragged();
    const room = LABEL_ROOM_PX / this.scale() ** 2;
    return this.shapes().map((s) => {
      const points = dragged && dragged.key === s.key ? dragged.points : s.points;
      return {
        key: s.key,
        kind: s.kind,
        d: pathOf(points),
        at: middleOf(points),
        color: this.colorOf(s),
        opacity: s.kind === 'structure' ? 0.3 : 0.25 + Math.min(s.floors, 8) * 0.06,
        open: s.floors === 0,
        label: areaOf(points) >= room || s.key === this.selectedKey() ? this.labelOf(s) : '',
      };
    });
  });

  /**
   * The selected shape's corners and walls to grab, and its measures: each wall's length, written
   * inside it, and the setback from the wall to the sidewalk in front of it - what a tape or the
   * phone's Measure app gives on site. Another building's wall in the way means no setback.
   */
  readonly handles = computed(() => {
    const s = this.selected();
    if (!s) return null;
    const dragged = this.dragged();
    const points = dragged && dragged.key === s.key ? dragged.points : s.points;
    const scale = this.scale();
    const sidewalks = this.view()?.sidewalkEdges ?? [];
    const blockers = this.shapes()
      .filter((o) => o.key !== s.key && (o.floors > 0 || o.basements > 0))
      .flatMap((o) => edgesOf(o.points));
    return {
      corners: points,
      mids: points.map((p, i) => {
        const q = points[(i + 1) % points.length];
        return { x: (p.x + q.x) / 2, y: (p.y + q.y) / 2 };
      }),
      walls: wallsOf(points, -12 / scale)
        .filter((w) => w.metres * scale >= 24)
        .map((w) => {
          const gap = distanceAhead(w.middle, w.out, sidewalks, blockers, SETBACK_REACH);
          return {
            ...w,
            text: `${w.metres.toFixed(2)} m`,
            setback:
              gap === null || gap < 0.05
                ? null
                : {
                    x2: w.middle.x + w.out.x * gap,
                    y2: w.middle.y + w.out.y * gap,
                    // Beside the dashed line, clear of the wall's square: a setback of a few
                    // centimetres would otherwise be written under it.
                    at: {
                      x: w.middle.x + (w.out.x * gap) / 2 - (w.out.y * 30) / scale,
                      y: w.middle.y + (w.out.y * gap) / 2 + (w.out.x * 30) / scale,
                    },
                    text: `${gap.toFixed(2)} m`,
                  },
          };
        }),
    };
  });

  /** The side panel's list: each building's parts by wing, then everything else. */
  readonly groups = computed(() => {
    const groups: { title: string; shapes: { key: string; text: string }[] }[] = [];
    for (const b of this.editable()) {
      const parts = this.shapes().filter((s) => s.kind === 'part' && s.building === b.code);
      groups.push({
        title: `${b.code} · ${b.name}`,
        shapes: parts
          .map((s) => ({ key: s.key, text: `${this.wingName(b, s.wing)} · ${floorsText(s)} · ${areaOf(s.points).toFixed(0)} m²`, sort: (s.wing ?? 'Z') + String(100 - s.floors).padStart(3, '0') }))
          .sort((x, y) => x.sort.localeCompare(y.sort))
          .map(({ key, text }) => ({ key, text })),
      });
    }
    const others = this.shapes().filter((s) => s.kind === 'structure');
    if (others.length) {
      groups.push({ title: t('Not the university’s'), shapes: others.map((s) => ({ key: s.key, text: `${s.name} · ${floorsText(s)}` })) });
    }
    return groups;
  });

  readonly wingKey = computed(() => {
    const key: { label: string; color: string }[] = [];
    for (const b of this.editable()) {
      const colors = wingColors(b);
      const used = new Set(this.shapes().filter((s) => s.building === b.code).map((s) => s.wing));
      for (const w of b.wings) {
        if (used.has(w.code)) key.push({ label: `${b.code} · ${w.name}`, color: colors.get(w.code)! });
      }
      if (used.has(null)) key.push({ label: b.code, color: BUILDING_COLOR });
    }
    return key;
  });

  /** Where north points on the screen, in degrees clockwise from its top. */
  readonly northAngle = computed(() => -(this.frame()?.up ?? 0));

  northWords(): string {
    const angle = ((this.northAngle() % 360) + 360) % 360;
    const words = [
      /* i18n */ 'up',
      /* i18n */ 'up and right',
      /* i18n */ 'right',
      /* i18n */ 'down and right',
      /* i18n */ 'down',
      /* i18n */ 'down and left',
      /* i18n */ 'left',
      /* i18n */ 'up and left',
    ];
    return t(words[Math.round(angle / 45) % 8]);
  }

  // ------------------------------------------------------------------ loading

  private load(block: string | undefined): void {
    if (block !== this.opened) {
      this.opened = block;
      this.fitted = false;
    }
    this.loading.set(true);
    this.error.set(null);
    this.buildingsService
      .list()
      .pipe(
        switchMap((all) => {
          const code = block ?? mainBlock(all);
          if (!block && code) {
            void this.router.navigate(['/data/blocks', code], { replaceUrl: true });
            return of(null);
          }
          const campus = all.find((b) => (b.footprint ?? []).some((p) => blockOf(p.lot) === code))?.campus ?? all[0]?.campus ?? '';
          const onCampus = all.filter((b) => b.campus === campus);
          return forkJoin({
            ground: this.grounds.forCampus(campus),
            structures: this.structuresService.forCampus(campus),
          }).pipe(switchMap(({ ground, structures }) => of({ campus, onCampus, ground, structures })));
        }),
      )
      .subscribe({
        next: (data) => {
          if (!data) return;
          this.campus.set(data.campus);
          this.buildings.set(data.onCampus);
          this.ground.set(data.ground);
          this.structuresVersion = data.structures.version;
          this.frame.set(frameFor(block ?? '', data.onCampus, data.ground));
          this.reset();
          this.restart(this.shapesFrom(data.onCampus, data.structures.structures));
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          this.error.set(err instanceof AppHttpError ? err.apiError : null);
        },
      });
  }

  private shapesFrom(buildings: Building[], structures: Structure[]): EditShape[] {
    const frame = this.frame()!;
    const block = this.block();
    const shapes: EditShape[] = [];
    for (const b of buildings) {
      if (!(b.footprint ?? []).some((p) => blockOf(p.lot) === block)) continue;
      (b.footprint ?? []).forEach((p, i) =>
        shapes.push({
          key: `part:${b.code}:${i}`,
          kind: 'part',
          building: b.code,
          lot: p.lot ?? null,
          wing: p.wing ?? null,
          name: '',
          floors: p.floors,
          lowestFloor: p.lowestFloor ?? 1,
          basements: p.basements,
          points: openRing(p.ring).map((c) => toView(frame, c)),
          original: p.ring,
          moved: false,
        }),
      );
    }
    structures.forEach((s, i) =>
      shapes.push({
        key: `structure:${i}`,
        kind: 'structure',
        building: null,
        lot: s.lot ?? null,
        wing: null,
        name: s.name,
        floors: s.floors,
        lowestFloor: 1,
        basements: s.basements,
        points: openRing(s.ring).map((c) => toView(frame, c)),
        original: s.ring,
        moved: false,
      }),
    );
    return shapes;
  }

  /** A fresh start from what was read: no history, nothing to save. */
  private restart(shapes: EditShape[]): void {
    this.loaded.set(shapes);
    this.shapes.set(shapes);
    this.past.set([]);
    this.future.set([]);
    this.selectedKey.set(null);
  }

  switchBlock(event: Event): void {
    const code = (event.target as HTMLSelectElement).value;
    if (code !== this.block() && this.canLeave()) void this.router.navigate(['/data/blocks', code]);
    else (event.target as HTMLSelectElement).value = this.block() ?? '';
  }

  // ------------------------------------------------------------------ editing

  select(key: string | null): void {
    this.selectedKey.set(key);
  }

  private commit(next: EditShape[]): void {
    this.past.update((p) => [...p.slice(-99), this.shapes()]);
    this.future.set([]);
    this.shapes.set(next);
    this.saveState.set('');
  }

  private update(key: string, change: (s: EditShape) => EditShape): void {
    this.commit(this.shapes().map((s) => (s.key === key ? change(s) : s)));
  }

  private reshape(key: string, points: ViewPoint[]): void {
    this.update(key, (s) => ({ ...s, points, moved: true }));
  }

  undo(): void {
    const past = this.past();
    if (!past.length) return;
    this.future.update((f) => [this.shapes(), ...f]);
    this.shapes.set(past[past.length - 1]);
    this.past.set(past.slice(0, -1));
  }

  redo(): void {
    const [next, ...rest] = this.future();
    if (!next) return;
    this.past.update((p) => [...p, this.shapes()]);
    this.shapes.set(next);
    this.future.set(rest);
  }

  discard(): void {
    this.restart(this.loaded());
    this.saveState.set(t('Discarded: back to what is saved.'));
  }

  square(): void {
    const s = this.selected();
    if (s) this.reshape(s.key, squared(s.points));
  }

  remove(): void {
    const s = this.selected();
    if (!s) return;
    this.commit(this.shapes().filter((x) => x.key !== s.key));
    this.selectedKey.set(null);
  }

  setName(event: Event): void {
    const s = this.selected();
    const name = (event.target as HTMLInputElement).value.trim();
    if (s && name) this.update(s.key, (x) => ({ ...x, name }));
  }

  setWing(event: Event): void {
    const s = this.selected();
    const wing = (event.target as HTMLSelectElement).value || null;
    if (s) this.update(s.key, (x) => ({ ...x, wing }));
  }

  setNumber(event: Event, field: 'floors' | 'basements' | 'lowestFloor'): void {
    const s = this.selected();
    const value = Math.round(Number((event.target as HTMLInputElement).value));
    if (!s || !Number.isFinite(value)) return;
    if (field === 'lowestFloor') {
      this.update(s.key, (x) => ({ ...x, lowestFloor: Math.max(1, Math.min(Math.max(1, x.floors), value)) }));
      return;
    }
    const max = field === 'floors' ? 200 : 20;
    // Fewer floors than the one it starts at would leave the part in no floor at all.
    this.update(s.key, (x) => {
      const next = { ...x, [field]: Math.max(0, Math.min(max, value)) };
      return { ...next, lowestFloor: Math.min(next.lowestFloor, Math.max(1, next.floors)) };
    });
  }

  addPart(building: string): void {
    this.add({ kind: 'part', building, lot: null, wing: null, name: '', floors: 1, lowestFloor: 1, basements: 0 });
  }

  addStructure(): void {
    this.add({ kind: 'structure', building: null, lot: null, wing: null, name: t('Something else'), floors: 1, lowestFloor: 1, basements: 0 });
  }

  private add(fields: Omit<EditShape, 'key' | 'points' | 'original' | 'moved'>): void {
    const box = this.view()?.box;
    if (!box) return;
    const key = `new:${Date.now()}`;
    const points = rectangleAt({ x: box.x + box.width / 2, y: box.y + box.height / 2 }, 10, 8);
    this.commit([...this.shapes(), { ...fields, key, points, original: null, moved: true }]);
    this.selectedKey.set(key);
  }

  turn(quarters: number): void {
    const frame = this.frame();
    if (!frame) return;
    // A quarter turn is exact on the screen's metres: no shape looks moved for it.
    const turned = (points: ViewPoint[]) => points.map((p) => (quarters > 0 ? { x: -p.y, y: p.x } : { x: p.y, y: -p.x }));
    const all = (shapes: EditShape[]) => shapes.map((s) => ({ ...s, points: turned(s.points) }));
    this.frame.set({ ...frame, up: (((frame.up - 90 * quarters) % 360) + 360) % 360 });
    this.loaded.update(all);
    this.shapes.update(all);
    this.past.update((p) => p.map(all));
    this.future.update((f) => f.map(all));
  }

  setZoom(step: number): void {
    this.zoom.set(Math.max(0, Math.min(ZOOMS.length - 1, step)));
  }

  // ------------------------------------------------------------------ pointer

  private gesture: Gesture | null = null;
  private start: { x: number; y: number; at: ViewPoint; pointerId: number } | null = null;
  private lastTap: { what: string; time: number } | null = null;

  onDown(event: PointerEvent): void {
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    const target = event.target as Element | null;
    const selected = this.selectedKey();
    const vertex = target?.closest('[data-vertex]')?.getAttribute('data-vertex');
    const edge = target?.closest('[data-edge]')?.getAttribute('data-edge');
    const key = target?.closest('[data-key]')?.getAttribute('data-key') ?? null;
    if (selected && vertex != null) this.gesture = { kind: 'vertex', key: selected, index: Number(vertex) };
    else if (selected && edge != null) this.gesture = { kind: 'edge', key: selected, index: Number(edge) };
    else if (selected && key === selected) this.gesture = { kind: 'move', key: selected };
    else this.gesture = { kind: 'tap', key };
    this.start = { x: event.clientX, y: event.clientY, at: this.pointAt(event), pointerId: event.pointerId };
    if (this.gesture.kind !== 'tap') this.svg()?.nativeElement.setPointerCapture?.(event.pointerId);
  }

  onMove(event: PointerEvent): void {
    const start = this.start;
    const gesture = this.gesture;
    if (!start || !gesture || gesture.kind === 'tap' || start.pointerId !== event.pointerId) return;
    if (!this.dragged() && Math.hypot(event.clientX - start.x, event.clientY - start.y) <= TAP_SLOP) return;
    const shape = this.shapes().find((s) => s.key === gesture.key);
    if (!shape) return;
    const at = this.pointAt(event);
    const reach = SNAP_PX / this.scale();
    switch (gesture.kind) {
      case 'vertex':
        this.dragged.set({ key: shape.key, points: withVertex(shape.points, gesture.index, snapped(at, this.targets(shape.key, gesture.index), reach)) });
        break;
      case 'edge':
        this.dragged.set({ key: shape.key, points: withEdgeMoved(shape.points, gesture.index, snapped(at, this.targets(shape.key), reach)) });
        break;
      case 'move': {
        const moved = translate(shape.points, at.x - start.at.x, at.y - start.at.y);
        const [dx, dy] = alignment(moved, this.targets(shape.key), reach);
        this.dragged.set({ key: shape.key, points: translate(moved, dx, dy) });
        break;
      }
    }
  }

  onUp(event: PointerEvent): void {
    const start = this.start;
    const gesture = this.gesture;
    if (!start || !gesture || start.pointerId !== event.pointerId) return;
    const dragged = this.dragged();
    this.reset();
    if (dragged) {
      this.reshape(dragged.key, dragged.points);
      return;
    }
    if (gesture.kind === 'vertex' || gesture.kind === 'edge') {
      const what = `${gesture.kind}:${gesture.key}:${gesture.index}`;
      const now = Date.now();
      if (this.lastTap && this.lastTap.what === what && now - this.lastTap.time < DOUBLE_TAP_MS) {
        this.lastTap = null;
        const shape = this.shapes().find((s) => s.key === gesture.key);
        if (!shape) return;
        if (gesture.kind === 'vertex') {
          const fewer = withoutVertex(shape.points, gesture.index);
          if (fewer) this.reshape(shape.key, fewer);
        } else {
          const a = shape.points[gesture.index];
          const b = shape.points[(gesture.index + 1) % shape.points.length];
          this.reshape(shape.key, withInsertedVertex(shape.points, gesture.index, { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }));
        }
      } else {
        this.lastTap = { what, time: now };
      }
      return;
    }
    if (gesture.kind === 'tap' || gesture.kind === 'move') this.select(gesture.kind === 'tap' ? gesture.key : gesture.key);
  }

  reset(): void {
    this.gesture = null;
    this.start = null;
    this.dragged.set(null);
  }

  /** The screen's point under the pointer, in metres. */
  private pointAt(event: PointerEvent): ViewPoint {
    const svg = this.svg()?.nativeElement;
    const box = this.view()?.box;
    if (!svg || !box) return { x: 0, y: 0 };
    const bounds = svg.getBoundingClientRect();
    return { x: (event.clientX - bounds.left) / this.scale() + box.x, y: (event.clientY - bounds.top) / this.scale() + box.y };
  }

  /** Every corner a dragged one can line up with: the other shapes', and this one's but the one dragged. */
  private targets(key: string, skip = -1): ViewPoint[] {
    return this.shapes().flatMap((s) => (s.key === key ? s.points.filter((_, i) => i !== skip) : s.points));
  }

  @HostListener('document:keydown', ['$event'])
  onKey(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    if (target && ['INPUT', 'SELECT', 'TEXTAREA'].includes(target.tagName)) return;
    const mod = event.metaKey || event.ctrlKey;
    if (mod && event.key.toLowerCase() === 'z') {
      event.preventDefault();
      if (event.shiftKey) this.redo();
      else this.undo();
    } else if (event.key === 'Escape') {
      this.select(null);
    }
  }

  // ------------------------------------------------------------------ saving

  save(): void {
    const frame = this.frame();
    if (!frame || this.saving()) return;
    const ring = (s: EditShape) => (!s.moved && s.original ? s.original : closeRing(s.points.map((p) => fromView(frame, p))));
    const writes: Observable<unknown>[] = [];
    for (const b of this.editable()) {
      const footprint: FootprintPart[] = this.shapes()
        .filter((s) => s.kind === 'part' && s.building === b.code)
        .map((s) => ({ lot: s.lot, floors: s.floors, lowestFloor: s.lowestFloor > 1 ? s.lowestFloor : null, basements: s.basements, wing: s.wing, ring: ring(s) }));
      if (JSON.stringify(footprint.map(partEssence)) === JSON.stringify((b.footprint ?? []).map(partEssence))) continue;
      writes.push(
        this.buildingsService.update(b.code, {
          code: b.code,
          name: b.name,
          campus: b.campus,
          description: b.description,
          aliases: b.aliases,
          wings: b.wings,
          floors: b.floors,
          placement: b.placement ?? null,
          footprint,
        }),
      );
    }
    const structures: Structure[] = this.shapes()
      .filter((s) => s.kind === 'structure')
      .map((s) => ({ name: s.name, floors: s.floors, basements: s.basements, lot: s.lot, ring: ring(s) }));
    const before = this.loaded().filter((s) => s.kind === 'structure').map((s) => ({ name: s.name, floors: s.floors, basements: s.basements, lot: s.lot, ring: s.original }));
    if (JSON.stringify(structures) !== JSON.stringify(before)) {
      writes.push(this.structuresService.save(this.campus(), { version: this.structuresVersion, structures }));
    }
    if (!writes.length) {
      this.restart(this.shapes());
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    concat(...writes)
      .pipe(last())
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.saveState.set(t('Saved. The floor editor draws its margins from what is saved here.'));
          this.load(this.block());
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(err instanceof AppHttpError ? err.apiError : null);
          this.saveState.set(
            err instanceof AppHttpError && err.apiError?.status === 409
              ? t('Somebody saved this block since you opened it. Nothing of yours was lost here: copy what you changed, then reload.')
              : t('Not saved.'),
          );
        },
      });
  }

  canLeave(): boolean {
    return !this.dirty() || window.confirm(t('This block has changes that are not saved. Leave and lose them?'));
  }

  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.dirty()) event.preventDefault();
  }

  // ------------------------------------------------------------------ words and colours

  titleOf(s: EditShape): string {
    if (s.kind === 'structure') return s.name;
    const b = this.buildings().find((x) => x.code === s.building);
    return b ? `${b.code} · ${this.wingName(b, s.wing)}` : (s.building ?? '');
  }

  wingsOf(code: string | null) {
    return this.buildings().find((b) => b.code === code)?.wings ?? [];
  }

  private wingName(b: Building, wing: string | null): string {
    return b.wings.find((w) => w.code === wing)?.name ?? t('No wing');
  }

  private colorOf(s: EditShape): string {
    if (s.kind === 'structure') return STRUCTURE_COLOR;
    const b = this.buildings().find((x) => x.code === s.building);
    return (b && s.wing && wingColors(b).get(s.wing)) || BUILDING_COLOR;
  }

  private labelOf(s: EditShape): string {
    if (s.kind === 'structure') return `${s.name.length > 20 ? s.name.slice(0, 19) + '…' : s.name} · ${floorsText(s)}`;
    return `${s.wing ? s.wing + ' · ' : ''}${floorsText(s)}`;
  }
}

/** What an edit changes: everything but the object identities. */
function essence(s: EditShape) {
  return { key: s.key, wing: s.wing, name: s.name, floors: s.floors, lowestFloor: s.lowestFloor, basements: s.basements, moved: s.moved, points: s.moved ? s.points : null };
}

function partEssence(p: FootprintPart) {
  return { lot: p.lot ?? null, floors: p.floors, lowestFloor: (p.lowestFloor ?? 1) > 1 ? p.lowestFloor : null, basements: p.basements, wing: p.wing ?? null, ring: p.ring };
}

function floorsText(s: Pick<EditShape, 'floors' | 'basements' | 'lowestFloor'>): string {
  const floors =
    s.lowestFloor > 1
      ? t('floors {from} to {to}', { from: s.lowestFloor, to: s.floors })
      : s.floors === 1
        ? t('1 floor')
        : t('{floors} floors', { floors: s.floors });
  return floors + (s.basements ? t(' + {basements} below', { basements: s.basements }) : '');
}

/** The block of the campus's building with the most parts: where a survey starts. */
function mainBlock(buildings: Building[]): string | null {
  const main = [...buildings].sort((a, b) => (b.footprint?.length ?? 0) - (a.footprint?.length ?? 0))[0];
  return blockOf(main?.footprint?.find((p) => p.lot)?.lot);
}

/**
 * The block turned so that its main building's walls run level and plumb, the side its drawing's
 * left edge faces at the top: for the Edificio Central, the Calle 63. North up for a block whose
 * buildings nobody has laid on the ground.
 */
function frameFor(block: string, buildings: Building[], ground: Ground | null): Frame {
  const here = buildings.filter((b) => (b.footprint ?? []).some((p) => blockOf(p.lot) === block));
  const main = [...here].sort((a, b) => (b.footprint?.length ?? 0) - (a.footprint?.length ?? 0))[0];
  const rings = [
    ...(ground?.lots ?? []).filter((l) => blockOf(l.code) === block).map((l) => l.ring),
    ...here.flatMap((b) => (b.footprint ?? []).map((p) => p.ring)),
  ].flat();
  const origin = rings.length
    ? { lon: rings.reduce((t, c) => t + c[0], 0) / rings.length, lat: rings.reduce((t, c) => t + c[1], 0) / rings.length }
    : { lon: 0, lat: 0 };
  const up = main?.placement ? (((main.placement.bearing - 90) % 360) + 360) % 360 : 0;
  return { origin, up };
}

function wingColors(building: Building): Map<string, string> {
  const colors = new Map<string, string>();
  let next = 0;
  for (const wing of building.wings) {
    colors.set(wing.code, WING_COLORS[wing.code] ?? MORE_WING_COLORS[next++ % MORE_WING_COLORS.length]);
  }
  return colors;
}

import { ChangeDetectionStrategy, Component, ElementRef, computed, input, output, signal, viewChild } from '@angular/core';
import type { Corridor, Point } from '../buildings/building.model';
import { CATEGORY_COLORS, type SpaceCategory } from '../spaces/space.model';
import { areaPath, streetLabel, type Box, type Margin, type Surroundings } from '../ground/ground';
import {
  boundsOf,
  isPlaced,
  isUnidentified,
  label,
  labelPoint,
  rectangle,
  translate,
  withInsertedVertex,
  withVertex,
  type DraftSpace,
} from './floor-draft';

export type EditorMode = 'select' | 'box' | 'corridor' | 'door' | 'split';

/** Movement under this many pixels is a tap, not a drag - a finger never lands perfectly still. */
const TAP_SLOP = 8;
/** A corner dragged this close to another corner's line, in pixels, lines up with it. */
const SNAP_PX = 7;
/** Two taps on one corner within this long remove it. */
const DOUBLE_TAP_MS = 400;

type Gesture =
  | { kind: 'tap'; key: string | null }
  | { kind: 'vertex'; key: string; index: number }
  | { kind: 'insert'; key: string; edge: number }
  | { kind: 'move'; key: string }
  | { kind: 'box' };

let uid = 0;

/**
 * The floor as the plan draws it: every room as its outline, its doors on it, stairs with their
 * treads, the building's walls around them and the corridors between.
 *
 * <p>Drawn in the floor's own units and scaled to the screen, so a floor traced from a plan keeps
 * its proportions at any zoom. The selected room can be dragged whole, reshaped by its corners
 * - dragging a small dot between two corners adds one, a double tap on a corner removes it - and
 * a corner let go near another room's corner line lines up with it, so two rooms sharing a wall
 * share it exactly.
 *
 * <p>Pointer Events rather than mouse or touch events, so a finger on the iPad, a pencil and a
 * mouse all go through the same code. Only what is being dragged takes the gesture over
 * (`touch-action: none`); elsewhere a finger still scrolls a floor larger than the screen.
 */
@Component({
  selector: 'app-floor-plan',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="surface" [class.drawing]="mode() !== 'select'" [class.disabled]="disabled()">
      <svg
        #svg
        role="application"
        [attr.aria-label]="'Floor plan, ' + width() + ' by ' + height() + ' units'"
        [attr.viewBox]="box().x + ' ' + box().y + ' ' + box().width + ' ' + box().height"
        [attr.width]="box().width * scale()"
        [attr.height]="box().height * scale()"
        (pointerdown)="onDown($event)"
        (pointermove)="onMove($event)"
        (pointerup)="onUp($event)"
        (pointercancel)="reset()"
      >
        <defs>
          <pattern [attr.id]="treadsId" patternUnits="userSpaceOnUse" [attr.width]="tread()" [attr.height]="tread()">
            <rect [attr.width]="tread()" [attr.height]="tread()" [attr.fill]="colors.CIRCULATION" />
            <line x1="0" y1="0" x2="0" [attr.y2]="tread()" stroke="#1c2128" stroke-opacity="0.45" vector-effect="non-scaling-stroke" />
          </pattern>
          <!-- The building ends where the sidewalk starts, on every floor: the margin and the
               cadastre's parts are cut where a sidewalk passes. -->
          @if (walkPaths().length) {
            <mask [attr.id]="walkMaskId" maskUnits="userSpaceOnUse" [attr.x]="box().x" [attr.y]="box().y"
                  [attr.width]="box().width" [attr.height]="box().height">
              <rect [attr.x]="box().x" [attr.y]="box().y" [attr.width]="box().width" [attr.height]="box().height" fill="#fff" />
              @for (d of walkPaths(); track $index) {
                <path [attr.d]="d" fill="#000" />
              }
            </mask>
          }
          @for (m of marginPaths()?.current ?? []; track $index) {
            <clipPath [attr.id]="marginClipId + '-' + $index"><path [attr.d]="m.d" /></clipPath>
          }
        </defs>

        @if (ground(); as g) {
          <g class="ground" aria-hidden="true">
            @for (d of g.roadways; track $index) {
              <path class="roadway" [attr.d]="d" />
            }
            @for (d of g.medians; track $index) {
              <path class="median" [attr.d]="d" />
            }
            @for (d of g.blocks; track $index) {
              <path class="block" [attr.d]="d" />
            }
            <!-- The sidewalks over the blocks. The blocks are the cadastre's and reach the curb in
                 places - along the Calle 63 the sidewalk runs under the Edificio Central's north
                 wing, inside its block - and a sidewalk hidden under a block reads as none. -->
            @for (d of g.sidewalks; track $index) {
              <path class="sidewalk" [attr.d]="d" />
            }
          </g>
        }

        <!-- Where this floor's rooms go, wing by wing, over the floor below's, dotted. -->
        @if (marginPaths(); as mp) {
          <g class="margins" aria-hidden="true">
            @for (m of mp.below; track $index) {
              <path [attr.class]="'margin below ' + m.wing" [attr.d]="m.d" />
            }
            <g [attr.mask]="walkPaths().length ? 'url(#' + walkMaskId + ')' : null">
              @for (m of mp.current; track $index) {
                <path [attr.class]="'margin ' + m.wing" [attr.d]="m.d" />
              }
            </g>
            <!-- Where the building meets a sidewalk its edge is the sidewalk's: the sidewalks'
                 outlines within the wing, half hidden under the sidewalk itself. -->
            @if (walkPaths().length) {
              @for (m of mp.current; track $index; let i = $index) {
                <g [attr.clip-path]="'url(#' + marginClipId + '-' + i + ')'" [attr.mask]="'url(#' + walkMaskId + ')'">
                  @for (d of walkPaths(); track $index) {
                    <path [attr.class]="'margin-edge ' + m.wing" [attr.d]="d" />
                  }
                </g>
              }
            }
          </g>
        }

        @if (ground(); as g) {
          <g class="ground" aria-hidden="true">
            <g [attr.mask]="walkPaths().length ? 'url(#' + walkMaskId + ')' : null">
              @for (part of g.footprint; track $index) {
                <path class="footprint" [class.reaches]="part.reaches" [attr.d]="part.d" />
              }
            </g>
            @for (street of g.labels; track $index) {
              <text
                class="street"
                [attr.transform]="'translate(' + street.x + ' ' + street.y + ') rotate(' + street.angle + ')'"
                [attr.font-size]="11 / scale()"
              >{{ street.name }}</text>
            }
          </g>
        }

        <rect class="paper" [class.over-ground]="!!ground()" [attr.width]="width()" [attr.height]="height()" />

        @if (outline().length > 2) {
          <polygon class="outline" [attr.points]="points(outline())" />
        }


        @for (room of rooms(); track room.space.key) {
          <polygon
            class="room"
            role="button"
            tabindex="0"
            [attr.data-key]="room.space.key"
            [class.selected]="room.space.key === selectedKey()"
            [class.unidentified]="room.unidentified"
            [class.problem]="problemKeys().has(room.space.key)"
            [class.refused]="room.refused"
            [attr.points]="points(room.shape)"
            [attr.fill]="room.stairs ? 'url(#' + treadsId + ')' : room.color"
            [attr.aria-label]="room.label"
            [attr.aria-pressed]="room.space.key === selectedKey()"
            (click)="onRoomClick(room.space.key)"
            (keydown.enter)="onRoomClick(room.space.key)"
            (keydown.space)="$event.preventDefault(); onRoomClick(room.space.key)"
          >
            <title>{{ room.label }}</title>
          </polygon>
        }

        @for (room of rooms(); track room.space.key) {
          @for (door of room.doors; track $index) {
            <line class="door-casing" [attr.x1]="door.from.x" [attr.y1]="door.from.y" [attr.x2]="door.to.x" [attr.y2]="door.to.y" />
            <line class="door" [attr.x1]="door.from.x" [attr.y1]="door.from.y" [attr.x2]="door.to.x" [attr.y2]="door.to.y" />
          }
        }

        <!-- The corridors over the rooms, so a room never hides a way through it, and under the
             names. They take no taps: a tap on a room still edits it. -->
        @for (corridor of showCorridors() ? corridors() : []; track $index; let i = $index) {
          <polyline
            class="corridor"
            [attr.points]="points(corridor.path)"
            [attr.stroke]="corridor.color"
            [attr.stroke-width]="i === activeCorridor() ? 8 : 6"
            [attr.opacity]="activeCorridor() === null || i === activeCorridor() ? 0.85 : 0.3"
          />
        }

        @for (room of rooms(); track room.space.key) {
          @if (room.fontSize > 0) {
            <text class="room-label" lang="es" [attr.x]="room.at.x" [attr.y]="room.at.y" [attr.font-size]="room.fontSize">
              {{ room.label }}
            </text>
          }
        }

        @if (activeCorridor() !== null) {
          @for (point of activePath(); track $index; let last = $last) {
            <circle class="corner" [attr.cx]="point.x" [attr.cy]="point.y" [attr.r]="(last ? 6 : 4) / scale()" [attr.fill]="activeColor()" />
          }
        }

        @if (handles(); as h) {
          @for (mid of h.mids; track $index) {
            <circle class="mid" [attr.data-edge]="$index" [attr.cx]="mid.x" [attr.cy]="mid.y" [attr.r]="5 / scale()" />
          }
          @for (corner of h.corners; track $index) {
            <circle class="handle" [attr.data-vertex]="$index" [attr.cx]="corner.x" [attr.cy]="corner.y" [attr.r]="8 / scale()" />
          }
        }

        @if (preview(); as box) {
          <polygon class="preview" [class.refused]="previewRefused()" [attr.points]="points(box)" />
        }
      </svg>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .surface {
      display: inline-block;
      line-height: 0;
      touch-action: manipulation;
      user-select: none;
      -webkit-user-select: none;
      -webkit-touch-callout: none;
    }
    .surface.drawing svg {
      touch-action: none;
      cursor: crosshair;
    }
    .surface.disabled {
      pointer-events: none;
      opacity: 0.5;
    }
    svg {
      display: block;
    }
    .paper {
      fill: var(--bg-elevated);
      stroke: color-mix(in srgb, var(--text) 14%, transparent);
      vector-effect: non-scaling-stroke;
    }
    /* Over the ground the frame is only where the plan was traced: the block shows through it. */
    .paper.over-ground {
      fill: none;
      stroke-dasharray: 6 4;
    }
    /* The city's layers, quiet enough that the floor stays what the eye goes to. The same in
       both themes but for the block, which takes the page's own paper. */
    .block {
      fill: var(--bg-elevated);
      stroke: color-mix(in srgb, var(--text) 45%, transparent);
      stroke-width: 1.5;
      vector-effect: non-scaling-stroke;
    }
    .roadway {
      fill: color-mix(in srgb, var(--text) 16%, var(--bg-elevated));
    }
    .median {
      fill: #b9d7a6;
    }
    .sidewalk {
      fill: color-mix(in srgb, #d8c9ad 70%, var(--bg-elevated));
      stroke: color-mix(in srgb, var(--text) 22%, transparent);
      stroke-width: 0.75;
      vector-effect: non-scaling-stroke;
    }
    /* The building as the cadastre has it: solid where it rises to this floor, dotted where it
       stops at the floor below, so a drawing that strays from the real walls shows it. */
    .footprint {
      fill: none;
      stroke: color-mix(in srgb, var(--nav-active-edge) 60%, transparent);
      stroke-width: 2;
      stroke-dasharray: 0 5;
      stroke-linecap: round;
      vector-effect: non-scaling-stroke;
    }
    .footprint.reaches {
      fill: color-mix(in srgb, var(--nav-active-edge) 7%, transparent);
      stroke: var(--nav-active-edge);
      stroke-dasharray: none;
    }
    /* The building's margin, wing by wing: close colours, so the wings read as one building. */
    .margin {
      --wing: var(--margin-other);
      fill: color-mix(in srgb, var(--wing) 11%, transparent);
      stroke: var(--wing);
      stroke-width: 2;
      stroke-linejoin: round;
      vector-effect: non-scaling-stroke;
    }
    .margin.wing-N,
    .margin-edge.wing-N {
      --wing: var(--margin-n);
    }
    .margin.wing-C,
    .margin.wing-none,
    .margin-edge.wing-C,
    .margin-edge.wing-none {
      --wing: var(--margin-c);
    }
    .margin.wing-S,
    .margin-edge.wing-S {
      --wing: var(--margin-s);
    }
    .margin.below {
      fill: none;
      stroke-width: 2.25;
      stroke-dasharray: 0 6;
      stroke-linecap: round;
      opacity: 0.85;
    }
    .margin-edge {
      --wing: var(--margin-other);
      fill: none;
      stroke: var(--wing);
      stroke-width: 4;
      vector-effect: non-scaling-stroke;
    }
    .street {
      fill: color-mix(in srgb, var(--text) 72%, transparent);
      font-weight: 600;
      text-anchor: middle;
      dominant-baseline: central;
      letter-spacing: 0.02em;
      paint-order: stroke;
      stroke: color-mix(in srgb, var(--text) 16%, var(--bg-elevated));
      stroke-width: 3;
      vector-effect: non-scaling-stroke;
    }
    .outline {
      fill: color-mix(in srgb, var(--text) 4%, transparent);
      stroke: color-mix(in srgb, var(--text) 70%, transparent);
      stroke-width: 2;
      vector-effect: non-scaling-stroke;
      pointer-events: none;
    }
    .corridor {
      fill: none;
      stroke-linecap: round;
      stroke-linejoin: round;
      vector-effect: non-scaling-stroke;
      pointer-events: none;
    }
    .room {
      stroke: rgb(28 33 40 / 55%);
      stroke-width: 1;
      stroke-linejoin: round;
      vector-effect: non-scaling-stroke;
      cursor: pointer;
    }
    .room.unidentified {
      stroke-dasharray: 5 3;
      stroke-width: 1.5;
    }
    .room.problem {
      stroke: var(--danger);
      stroke-width: 2;
    }
    .room.selected {
      stroke: var(--primary);
      stroke-width: 3;
      touch-action: none;
      cursor: move;
    }
    .room.refused {
      stroke: var(--danger);
      stroke-width: 3;
    }
    .room:focus-visible {
      outline: none;
      stroke: var(--focus-ring);
      stroke-width: 3;
    }
    .door-casing {
      stroke: #fff;
      stroke-width: 7;
      vector-effect: non-scaling-stroke;
      pointer-events: none;
    }
    .door {
      stroke: #0f766e;
      stroke-width: 4;
      vector-effect: non-scaling-stroke;
      pointer-events: none;
    }
    .room-label {
      /* The fills are pale in both themes, so the ink is fixed rather than the theme's text. */
      fill: #1c2128;
      font-weight: 600;
      text-anchor: middle;
      dominant-baseline: central;
      paint-order: stroke;
      stroke: rgb(255 255 255 / 70%);
      stroke-width: 3;
      vector-effect: non-scaling-stroke;
      pointer-events: none;
    }
    .corner {
      stroke: #fff;
      stroke-width: 1.5;
      vector-effect: non-scaling-stroke;
      pointer-events: none;
    }
    .handle {
      fill: #fff;
      stroke: var(--primary);
      stroke-width: 2.5;
      vector-effect: non-scaling-stroke;
      touch-action: none;
      cursor: grab;
    }
    .mid {
      fill: var(--primary);
      fill-opacity: 0.55;
      stroke: #fff;
      stroke-width: 1.5;
      vector-effect: non-scaling-stroke;
      touch-action: none;
      cursor: copy;
    }
    .preview {
      fill: color-mix(in srgb, var(--primary) 18%, transparent);
      stroke: var(--primary);
      stroke-width: 2;
      stroke-dasharray: 6 4;
      vector-effect: non-scaling-stroke;
      pointer-events: none;
    }
    .preview.refused {
      fill: color-mix(in srgb, var(--danger) 18%, transparent);
      stroke: var(--danger);
    }
  `,
})
export class FloorPlanComponent {
  readonly width = input.required<number>();
  readonly height = input.required<number>();
  /** Screen pixels per unit of the drawing. */
  readonly scale = input(1);
  readonly spaces = input<DraftSpace[]>([]);
  readonly outline = input<Point[]>([]);
  readonly corridors = input<Corridor[]>([]);
  readonly categories = input<ReadonlyMap<string, SpaceCategory>>(new Map());
  readonly selectedKey = input<string | null>(null);
  readonly problemKeys = input<ReadonlySet<string>>(new Set());
  readonly activeCorridor = input<number | null>(null);
  readonly mode = input<EditorMode>('select');
  readonly disabled = input(false);
  /** Asked while a room is being drawn or reshaped: whether that outline can be `key`'s. */
  readonly canPlace = input<(key: string | null, shape: Point[]) => boolean>(() => true);
  /**
   * The part of the plane shown, in the floor's units. The drawing by default; wider when the
   * streets around the building are drawn too, since the frame the plans were traced in stops at
   * the lot.
   */
  readonly view = input<Box | null>(null);
  /** The block, sidewalks and streets around the building, in the floor's units. */
  readonly surroundings = input<Surroundings | null>(null);
  /** Where this floor's rooms go, by wing, and where the floor below's went. */
  readonly margins = input<{ current: Margin[]; below: Margin[] } | null>(null);
  /** The sidewalks along the building: nothing of it is drawn over them. */
  readonly sidewalks = input<Point[][]>([]);
  /** Whether the rooms are drawn; off, only what is under them shows. */
  readonly showRooms = input(true);
  /** Whether the corridors are drawn. */
  readonly showCorridors = input(true);

  readonly walkPaths = computed(() => this.sidewalks().map(areaPath));
  readonly marginPaths = computed(() => {
    const margins = this.margins();
    if (!margins) return null;
    const path = (m: Margin) => ({ d: areaPath(m.outline), wing: `wing-${m.wing ?? 'none'}` });
    return { current: margins.current.map(path), below: margins.below.map(path) };
  });

  readonly box = computed<Box>(() => this.view() ?? { x: 0, y: 0, width: this.width(), height: this.height() });

  /** The surroundings as paths, and where each street's name goes at this zoom. */
  readonly ground = computed(() => {
    const around = this.surroundings();
    if (!around) return null;
    const box = this.box();
    const minLength = 90 / this.scale();
    const seen = new Set<string>();
    const labels: { name: string; x: number; y: number; angle: number }[] = [];
    for (const street of around.streets) {
      const at = streetLabel(street.path, box, minLength);
      // One name per street and stretch: a street cut into pieces by the service would say it twice.
      const key = `${street.name}@${at ? Math.round(at.x / (200 / this.scale())) + ',' + Math.round(at.y / (200 / this.scale())) : ''}`;
      if (at && !seen.has(key)) {
        seen.add(key);
        labels.push({ name: street.name, ...at });
      }
    }
    return {
      blocks: around.blocks.map(areaPath),
      sidewalks: around.sidewalks.map(areaPath),
      roadways: around.roadways.map(areaPath),
      medians: around.medians.map(areaPath),
      footprint: around.footprint.map((part) => ({ d: areaPath(part.outline), reaches: part.reaches })),
      labels,
    };
  });

  /** A tap on the plan that is not a room's: where, in the drawing's units. */
  readonly pointTap = output<Point>();
  readonly spaceTap = output<string>();
  readonly boxDrawn = output<Point[]>();
  readonly moved = output<{ key: string; dx: number; dy: number }>();
  readonly reshaped = output<{ key: string; shape: Point[] }>();
  readonly vertexRemoved = output<{ key: string; index: number }>();

  private readonly svg = viewChild.required<ElementRef<SVGSVGElement>>('svg');

  readonly colors = CATEGORY_COLORS;
  readonly treadsId = `treads-${++uid}`;
  readonly walkMaskId = `walks-${uid}`;
  readonly marginClipId = `margin-${uid}`;

  /** A room being dragged or reshaped, drawn where the finger has it until it is let go. */
  private readonly dragged = signal<{ key: string; shape: Point[] } | null>(null);
  readonly preview = signal<Point[] | null>(null);

  readonly previewRefused = computed(() => {
    const box = this.preview();
    return box !== null && !this.canPlace()(null, box);
  });

  /** How far apart the treads of a staircase are drawn: about four pixels at any zoom. */
  readonly tread = computed(() => 4 / this.scale());

  readonly rooms = computed(() => {
    const dragged = this.dragged();
    const scale = this.scale();
    if (!this.showRooms()) return [];
    return this.spaces()
      .filter(isPlaced)
      .map((space) => {
        const moving = dragged?.key === space.key ? dragged : null;
        const shape = moving ? moving.shape : (space.shape as Point[]);
        const dx = moving ? shape[0].x - (space.shape as Point[])[0].x : 0;
        const dy = moving ? shape[0].y - (space.shape as Point[])[0].y : 0;
        const box = boundsOf(shape);
        const text = label(space);
        const category = this.categories().get(space.typeCode) ?? 'OTHER';
        // A readable label where the room is big enough on screen for one, and none where not.
        const fit = Math.min(13, (box.width * scale) / Math.max(3, text.length * 0.62), (box.height * scale) / 1.6);
        return {
          space,
          shape,
          label: text,
          at: labelPoint(shape),
          fontSize: fit >= 7 ? fit / scale : 0,
          color: CATEGORY_COLORS[category],
          stairs: space.typeCode === 'STAIRS',
          unidentified: isUnidentified(space),
          refused: !!moving && !this.canPlace()(space.key, shape),
          doors: moving && moving.shape.length === (space.shape as Point[]).length
            ? space.doors.map((d) => ({ from: { x: d.from.x + dx, y: d.from.y + dy }, to: { x: d.to.x + dx, y: d.to.y + dy } }))
            : space.doors,
        };
      });
  });

  readonly selectedRoom = computed(() => this.rooms().find((r) => r.space.key === this.selectedKey()) ?? null);

  readonly handles = computed(() => {
    const room = this.selectedRoom();
    if (!room || this.mode() !== 'select' || this.disabled()) return null;
    const shape = room.shape;
    return {
      corners: shape,
      mids: shape.map((p, i) => {
        const q = shape[(i + 1) % shape.length];
        return { x: (p.x + q.x) / 2, y: (p.y + q.y) / 2 };
      }),
    };
  });

  readonly activePath = computed(() => {
    const index = this.activeCorridor();
    return index === null ? [] : (this.corridors()[index]?.path ?? []);
  });
  readonly activeColor = computed(() => {
    const index = this.activeCorridor();
    return index === null ? '#000' : (this.corridors()[index]?.color ?? '#000');
  });

  private gesture: Gesture | null = null;
  private start: { x: number; y: number; at: Point; pointerId: number } | null = null;
  private lastCornerTap: { key: string; index: number; time: number } | null = null;

  points(points: Point[]): string {
    return points.map((p) => `${p.x},${p.y}`).join(' ');
  }

  onDown(event: PointerEvent): void {
    if (this.disabled() || (event.pointerType === 'mouse' && event.button !== 0)) return;
    const at = this.pointAt(event);
    const target = event.target as Element | null;
    const selected = this.selectedKey();
    const vertex = target?.closest('[data-vertex]')?.getAttribute('data-vertex');
    const edge = target?.closest('[data-edge]')?.getAttribute('data-edge');
    const room = target?.closest('[data-key]')?.getAttribute('data-key') ?? null;

    if (this.mode() === 'box') {
      this.gesture = { kind: 'box' };
    } else if (this.mode() === 'select' && selected && vertex != null) {
      this.gesture = { kind: 'vertex', key: selected, index: Number(vertex) };
    } else if (this.mode() === 'select' && selected && edge != null) {
      this.gesture = { kind: 'insert', key: selected, edge: Number(edge) };
    } else if (this.mode() === 'select' && selected && room === selected) {
      this.gesture = { kind: 'move', key: selected };
    } else {
      this.gesture = { kind: 'tap', key: room };
    }
    this.start = { x: event.clientX, y: event.clientY, at, pointerId: event.pointerId };
    if (this.gesture.kind !== 'tap') {
      this.svg().nativeElement.setPointerCapture?.(event.pointerId);
    }
  }

  onMove(event: PointerEvent): void {
    const start = this.start;
    const gesture = this.gesture;
    if (!start || !gesture || start.pointerId !== event.pointerId || gesture.kind === 'tap') return;
    if (Math.hypot(event.clientX - start.x, event.clientY - start.y) <= TAP_SLOP && !this.dragged() && !this.preview()) return;
    const at = this.pointAt(event, true);
    const shape = this.shapeOf(gesture.kind === 'box' ? null : gesture.key);

    switch (gesture.kind) {
      case 'box': {
        const a = this.snap(start.at, []);
        const b = this.snap(at, []);
        this.preview.set(rectangle({ x: Math.min(a.x, b.x), y: Math.min(a.y, b.y), width: Math.abs(b.x - a.x), height: Math.abs(b.y - a.y) }));
        break;
      }
      case 'vertex':
        if (shape) this.dragged.set({ key: gesture.key, shape: withVertex(shape, gesture.index, this.snap(at, [gesture.key, gesture.index])) });
        break;
      case 'insert':
        if (shape) this.dragged.set({ key: gesture.key, shape: withInsertedVertex(shape, gesture.edge, this.snap(at, [gesture.key, -1])) });
        break;
      case 'move':
        if (shape) {
          const moved = translate(shape, Math.round(at.x - start.at.x), Math.round(at.y - start.at.y));
          const [dx, dy] = this.alignment(moved, gesture.key);
          this.dragged.set({ key: gesture.key, shape: translate(moved, dx, dy) });
        }
        break;
    }
  }

  onUp(event: PointerEvent): void {
    const start = this.start;
    const gesture = this.gesture;
    if (!start || !gesture || start.pointerId !== event.pointerId) return;
    const dragged = this.dragged();
    const drawn = this.preview();
    const at = this.pointAt(event);
    this.reset();

    if (drawn) {
      if (drawn[1].x - drawn[0].x >= 1 && drawn[2].y - drawn[1].y >= 1 && this.canPlace()(null, drawn)) this.boxDrawn.emit(drawn);
      return;
    }
    if (dragged) {
      if (gesture.kind === 'move') {
        const before = this.shapeOf(gesture.key);
        if (before) this.moved.emit({ key: gesture.key, dx: dragged.shape[0].x - before[0].x, dy: dragged.shape[0].y - before[0].y });
      } else {
        this.reshaped.emit(dragged);
      }
      return;
    }

    // A tap.
    if (gesture.kind === 'vertex') {
      const last = this.lastCornerTap;
      const now = Date.now();
      if (last && last.key === gesture.key && last.index === gesture.index && now - last.time < DOUBLE_TAP_MS) {
        this.lastCornerTap = null;
        this.vertexRemoved.emit({ key: gesture.key, index: gesture.index });
      } else {
        this.lastCornerTap = { key: gesture.key, index: gesture.index, time: now };
      }
      return;
    }
    if (gesture.kind === 'insert') return;
    const round = { x: Math.round(at.x), y: Math.round(at.y) };
    // A tap on a room is its click's to handle - the click is also what a screen reader sends -
    // except where the tap means a point: a corridor's corner, a door on a wall, a room drawn.
    if (this.mode() === 'select' && (gesture.kind === 'move' || (gesture.kind === 'tap' && gesture.key))) return;
    this.pointTap.emit(round);
  }

  onRoomClick(key: string): void {
    if (!this.disabled() && this.mode() === 'select') this.spaceTap.emit(key);
  }

  reset(): void {
    this.gesture = null;
    this.start = null;
    this.dragged.set(null);
    this.preview.set(null);
  }

  private shapeOf(key: string | null): Point[] | null {
    const space = key ? this.spaces().find((s) => s.key === key) : null;
    return space && isPlaced(space) ? space.shape : null;
  }

  /** The drawing's point under the pointer; kept on the drawing while a drag strays off it. */
  private pointAt(event: PointerEvent, clamp = false): Point {
    const bounds = this.svg().nativeElement.getBoundingClientRect();
    const scale = this.scale();
    const box = this.box();
    let x = (event.clientX - bounds.left) / scale + box.x;
    let y = (event.clientY - bounds.top) / scale + box.y;
    if (clamp) {
      x = Math.max(0, Math.min(this.width(), x));
      y = Math.max(0, Math.min(this.height(), y));
    }
    return { x, y };
  }

  /** Every corner on the floor a new one can line up with, but the one being dragged. */
  private targets(skip: [string, number] | []): Point[] {
    const [skipKey, skipIndex] = skip;
    const corners: Point[] = [...this.outline(), { x: 0, y: 0 }, { x: this.width(), y: this.height() }];
    for (const space of this.spaces()) {
      if (!isPlaced(space)) continue;
      space.shape.forEach((p, i) => {
        if (!(space.key === skipKey && i === skipIndex)) corners.push(p);
      });
    }
    return corners;
  }

  /** A corner at `p`, lined up with the nearest corner line within a few pixels, in whole units. */
  private snap(p: Point, skip: [string, number] | []): Point {
    const reach = SNAP_PX / this.scale();
    let x = Math.round(p.x);
    let y = Math.round(p.y);
    let bestX = reach;
    let bestY = reach;
    for (const q of this.targets(skip)) {
      if (Math.abs(q.x - p.x) < bestX) {
        bestX = Math.abs(q.x - p.x);
        x = q.x;
      }
      if (Math.abs(q.y - p.y) < bestY) {
        bestY = Math.abs(q.y - p.y);
        y = q.y;
      }
    }
    return { x: Math.max(0, Math.min(this.width(), x)), y: Math.max(0, Math.min(this.height(), y)) };
  }

  /** The nudge that lines a moved room's corners up with another room's, within a few pixels. */
  private alignment(shape: Point[], key: string): [number, number] {
    const reach = SNAP_PX / this.scale();
    const others = this.spaces().filter((s) => s.key !== key && isPlaced(s)).flatMap((s) => s.shape as Point[]);
    let dx = 0;
    let dy = 0;
    let bestX = reach;
    let bestY = reach;
    for (const p of shape) {
      for (const q of others) {
        if (Math.abs(q.x - p.x) < bestX) {
          bestX = Math.abs(q.x - p.x);
          dx = q.x - p.x;
        }
        if (Math.abs(q.y - p.y) < bestY) {
          bestY = Math.abs(q.y - p.y);
          dy = q.y - p.y;
        }
      }
    }
    return [dx, dy];
  }
}

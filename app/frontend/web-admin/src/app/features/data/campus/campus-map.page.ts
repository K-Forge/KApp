import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, map, of, switchMap } from 'rxjs';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import type { Building, Floor, GeoPoint } from '../buildings/building.model';
import { BuildingsService } from '../buildings/buildings.service';
import { FloorsService } from '../floors/floors.service';
import type { FloorDetail } from '../floors/floor.model';
import { areaPath, drawingToGround, placementForFloor, streetLabel, toMetres, type Box } from '../ground/ground';
import type { Coordinate, Ground } from '../ground/ground.model';
import { GroundService } from '../ground/ground.service';
import { CATEGORY_COLORS } from '../spaces/space.model';

/** A point on the map: metres east of its centre, and south, so the SVG's y grows down. */
interface MapPoint {
  x: number;
  y: number;
}

interface MappedBuilding {
  code: string;
  name: string;
  /** The floor its link opens: the one it meets the street on. */
  floor: string | null;
  floors: number;
  parts: { d: string; fill: string; opacity: number; wing: string | null }[];
  rooms: { d: string; fill: string }[];
  label: MapPoint;
}

/** Pixels per metre at each zoom step. */
const ZOOMS = [0.6, 0.9, 1.3, 2, 3, 4.5, 6.5];
/** From this step on, a drawn ground floor shows its rooms over the building. */
const ROOMS_FROM = 4;

/**
 * Wing colours, by code where the campus has settled on one: the Edificio Central's north wing
 * red, its central one blue and its south one purple, as the photograph of its front shows them.
 * Any other wing takes the next of the rest.
 */
const WING_COLORS: Record<string, string> = { N: '#e0564f', C: '#3b82f6', S: '#8b5cf6' };
const MORE_WING_COLORS = ['#0d9488', '#d97706', '#db2777', '#65a30d'];
/** A building whose parts carry no wing. */
const BUILDING_COLOR = '#c2185b';

/**
 * The campus from above: the city's blocks and streets, and on them every building of the
 * university, part by part as the cadastre records it.
 *
 * <p>North is up, as on any map. The distance between two buildings is the distance on the street
 * - this is the screen that answers "how far is the CPC from the Edificio Central". A building's
 * parts are coloured by wing where somebody has said which is which, and darker the more floors
 * they rise; zoomed in, a building drawn from its plans shows the rooms of its ground floor.
 */
@Component({
  selector: 'app-campus-map-page',
  imports: [RouterLink, PageIntroComponent, ApiErrorBannerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        title="Campus map"
        what="The campus from above: the city's blocks and streets, and every building of the university on them, in its place and to scale."
        [can]="['See where each building is and how far apart they are', 'Tell a building\\'s wings apart', 'Zoom in to see the rooms of a ground floor', 'Open a building\\'s floors from the map']"
        note="North is up. The buildings and the streets are the city's own cadastre and reference map; a building drawn from its plans is laid over its footprint by where its drawing's corner is, which way it faces and at what scale."
      />

      <app-api-error-banner [error]="error()" />

      @if (loading()) {
        <div class="card empty-state">Loading the campus…</div>
      } @else if (!ground()) {
        <div class="card empty-state">
          <p>There is no map of the ground for {{ campus() }} yet.</p>
        </div>
      } @else {
        <section class="card map-card">
          <div class="row-between toolbar">
            <strong>{{ campus() }}</strong>
            <div class="row">
              <button type="button" class="btn btn-sm" aria-label="Zoom out" [disabled]="zoom() === 0" (click)="setZoom(zoom() - 1)">−</button>
              <button type="button" class="btn btn-sm" aria-label="Zoom in" [disabled]="zoom() === zooms.length - 1" (click)="setZoom(zoom() + 1)">+</button>
            </div>
          </div>
          <div class="scroller" #scroller>
            @if (drawn(); as m) {
              <svg
                role="img"
                [attr.aria-label]="'Map of ' + campus() + ', north up'"
                [attr.viewBox]="m.box.x + ' ' + m.box.y + ' ' + m.box.width + ' ' + m.box.height"
                [attr.width]="m.box.width * zooms[zoom()]"
                [attr.height]="m.box.height * zooms[zoom()]"
              >
                @for (d of m.roadways; track $index) {
                  <path class="roadway" [attr.d]="d" />
                }
                @for (d of m.medians; track $index) {
                  <path class="median" [attr.d]="d" />
                }
                @for (d of m.blocks; track $index) {
                  <path class="block" [attr.d]="d" />
                }
                <!-- The sidewalks over the blocks, which reach the curb in places; the buildings,
                     seen from above as the cadastre maps them, over both: a floor that hangs over
                     a sidewalk hides it here as it does from the air. -->
                @for (d of m.sidewalks; track $index) {
                  <path class="sidewalk" [attr.d]="d" />
                }
                @for (s of m.labels; track $index) {
                  <text class="street" [attr.transform]="'translate(' + s.x + ' ' + s.y + ') rotate(' + s.angle + ')'" [attr.font-size]="11 / zooms[zoom()]">{{ s.name }}</text>
                }
                @for (b of mapped(); track b.code) {
                  <a [routerLink]="b.floor ? ['/data/floors', b.code, b.floor] : ['/data/buildings']" class="building" [attr.aria-label]="b.name + ' - open its floors'">
                    <title>{{ b.name }} · {{ b.floors }} {{ b.floors === 1 ? 'floor' : 'floors' }}</title>
                    @for (part of b.parts; track $index) {
                      <path class="part" [attr.d]="part.d" [attr.fill]="part.fill" [attr.fill-opacity]="part.opacity" [attr.stroke]="part.fill" />
                    }
                    @if (zoom() >= roomsFrom) {
                      @for (room of b.rooms; track $index) {
                        <path class="room" [attr.d]="room.d" [attr.fill]="room.fill" />
                      }
                    }
                    <text class="code" [attr.x]="b.label.x" [attr.y]="b.label.y" [attr.font-size]="13 / zooms[zoom()]">{{ b.code }}</text>
                  </a>
                }
                <g class="north" [attr.transform]="'translate(' + (m.box.x + 26 / zooms[zoom()]) + ' ' + (m.box.y + 30 / zooms[zoom()]) + ') scale(' + 1 / zooms[zoom()] + ')'">
                  <circle r="16" />
                  <path d="M0 -11 L5 5 L0 2 L-5 5 Z" />
                  <text y="-22" text-anchor="middle" font-size="10" font-weight="700">N</text>
                </g>
              </svg>
            }
          </div>

          <div class="key">
            @for (w of wingKey(); track w.code) {
              <span class="key-item"><span class="swatch" [style.background]="w.color"></span>{{ w.label }}</span>
            }
            <span class="key-item"><span class="swatch" [style.background]="buildingColor"></span>A building, part by part</span>
            <span class="key-item"><span class="swatch ramp"></span>Darker rises higher</span>
            @if (zoom() < roomsFrom) {
              <span class="key-item hint">Zoom in to see the rooms of each drawn ground floor</span>
            }
          </div>
          <p class="credit">Buildings, blocks and streets: {{ ground()?.source }}</p>
        </section>

        <section class="card">
          <h2 class="h">The buildings</h2>
          <ul class="buildings">
            @for (b of mapped(); track b.code) {
              <li>
                <strong>{{ b.code }}</strong>
                @if (b.floor) {
                  <a [routerLink]="['/data/floors', b.code, b.floor]">{{ b.name }}</a>
                } @else {
                  {{ b.name }}
                }
                <span class="text-muted">· {{ b.floors }} {{ b.floors === 1 ? 'floor' : 'floors' }}</span>
              </li>
            }
          </ul>
          @if (unmapped().length) {
            <h3 class="h3">Not on the map yet</h3>
            <p class="text-muted">
              Neither laid on the ground nor taken from the cadastre: nobody has said yet where they stand.
            </p>
            <ul class="buildings">
              @for (b of unmapped(); track b.code) {
                <li><strong>{{ b.code }}</strong> {{ b.name }}</li>
              }
            </ul>
          }
        </section>
      }
    </div>
  `,
  styles: `
    .map-card {
      padding: 0.75rem;
    }
    .toolbar {
      margin-bottom: 0.5rem;
    }
    .scroller {
      overflow: auto;
      max-height: 72vh;
      border-radius: var(--radius-sm);
      background: color-mix(in srgb, var(--text) 16%, var(--bg-elevated));
      -webkit-overflow-scrolling: touch;
    }
    svg {
      display: block;
    }
    .block {
      fill: var(--bg-elevated);
      stroke: color-mix(in srgb, var(--text) 40%, transparent);
      stroke-width: 1;
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
    }
    .street {
      fill: color-mix(in srgb, var(--text) 72%, transparent);
      font-weight: 600;
      text-anchor: middle;
      dominant-baseline: central;
      paint-order: stroke;
      stroke: color-mix(in srgb, var(--text) 16%, var(--bg-elevated));
      stroke-width: 3;
      vector-effect: non-scaling-stroke;
    }
    .part {
      stroke-width: 1;
      stroke-opacity: 0.9;
      vector-effect: non-scaling-stroke;
    }
    .room {
      stroke: rgb(28 33 40 / 45%);
      stroke-width: 0.5;
      vector-effect: non-scaling-stroke;
    }
    .code {
      fill: #1c2128;
      font-weight: 800;
      text-anchor: middle;
      dominant-baseline: central;
      paint-order: stroke;
      stroke: rgb(255 255 255 / 85%);
      stroke-width: 4;
      vector-effect: non-scaling-stroke;
      pointer-events: none;
    }
    .building {
      cursor: pointer;
    }
    .building:hover .part,
    .building:focus-visible .part {
      stroke-width: 3;
    }
    .north circle {
      fill: var(--bg-elevated);
      stroke: color-mix(in srgb, var(--text) 30%, transparent);
    }
    .north path,
    .north text {
      fill: var(--text);
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
    .key .hint {
      color: var(--text-faint);
    }
    .swatch {
      width: 0.9rem;
      height: 0.9rem;
      border-radius: 3px;
      opacity: 0.8;
    }
    .swatch.ramp {
      background: linear-gradient(90deg, color-mix(in srgb, #c2185b 25%, #fff), #c2185b);
      opacity: 1;
    }
    .credit {
      margin: 0.5rem 0 0;
      font-size: 0.6875rem;
      color: var(--text-faint);
    }
    .h {
      font-size: 1rem;
    }
    .h3 {
      font-size: 0.875rem;
      margin-top: 1rem;
    }
    .buildings {
      margin: 0;
      padding-left: 1.1rem;
      display: grid;
      gap: 0.25rem;
    }
  `,
})
export class CampusMapPage {
  private readonly buildings = inject(BuildingsService);
  private readonly floors = inject(FloorsService);
  private readonly grounds = inject(GroundService);

  readonly zooms = ZOOMS;
  readonly roomsFrom = ROOMS_FROM;
  readonly buildingColor = BUILDING_COLOR;
  readonly zoom = signal(3);
  readonly loading = signal(true);
  readonly error = signal<ApiError | null>(null);
  readonly campus = signal('Sede Principal');
  readonly ground = signal<Ground | null>(null);
  readonly all = signal<Building[]>([]);
  readonly details = signal<Map<string, FloorDetail>>(new Map());
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');
  /** The point of the map in the middle of the screen, as a share of its width and height. */
  private centreShare = { x: 0.5, y: 0.5 };

  constructor() {
    // Opened on the buildings, and zoomed about the middle of the screen rather than its corner.
    effect(() => {
      const element = this.scroller()?.nativeElement;
      if (!this.drawn() || !element) return;
      this.zoom();
      requestAnimationFrame(() => {
        element.scrollLeft = element.scrollWidth * this.centreShare.x - element.clientWidth / 2;
        element.scrollTop = element.scrollHeight * this.centreShare.y - element.clientHeight / 2;
      });
    });

    this.buildings
      .list()
      .pipe(
        switchMap((buildings) => {
          // The campus with the most buildings is the one to show first.
          const counts = new Map<string, number>();
          buildings.forEach((b) => counts.set(b.campus, (counts.get(b.campus) ?? 0) + 1));
          const campus = [...counts].sort((a, b) => b[1] - a[1])[0]?.[0] ?? this.campus();
          const onCampus = buildings.filter((b) => b.campus === campus);
          const drawn = onCampus.filter((b) => b.placement && groundFloor(b)?.status !== 'UNMAPPED' && groundFloor(b));
          const floors = drawn.length
            ? forkJoin(drawn.map((b) => this.floors.get(b.code, groundFloor(b)!.code)))
            : of([] as FloorDetail[]);
          return forkJoin({ ground: this.grounds.forCampus(campus), floors }).pipe(
            map(({ ground, floors }) => ({ campus, onCampus, ground, floors })),
          );
        }),
      )
      .subscribe({
        next: ({ campus, onCampus, ground, floors }) => {
          this.campus.set(campus);
          this.all.set(onCampus);
          this.ground.set(ground);
          this.details.set(new Map(floors.map((f) => [f.buildingCode, f])));
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          this.error.set(err instanceof AppHttpError ? err.apiError : null);
        },
      });
  }

  /** The middle of the map: the middle of the buildings on it, or of the ground. */
  private readonly centre = computed<GeoPoint | null>(() => {
    const points = this.all().flatMap((b) =>
      b.footprint?.length ? b.footprint.flatMap((p) => p.ring) : b.placement ? [[b.placement.origin.lon, b.placement.origin.lat] as [number, number]] : [],
    );
    if (points.length) {
      return {
        lat: points.reduce((t, c) => t + c[1], 0) / points.length,
        lon: points.reduce((t, c) => t + c[0], 0) / points.length,
      };
    }
    const first = this.ground()?.blocks[0]?.[0];
    return first ? { lat: first[1], lon: first[0] } : null;
  });

  private toMap(c: Coordinate): MapPoint {
    const centre = this.centre()!;
    const { east, north } = toMetres(centre, c);
    return { x: east, y: -north };
  }

  /** Every building with a place on the map: its parts from the cadastre, its drawn ground floor. */
  readonly mapped = computed<MappedBuilding[]>(() => {
    if (!this.centre()) return [];
    const out: MappedBuilding[] = [];
    for (const building of this.all()) {
      const wingColor = wingColors(building);
      const parts = (building.footprint ?? []).map((part) => {
        const points = part.ring.map((c) => this.toMap(c));
        return {
          points,
          d: areaPath(points),
          fill: part.wing ? (wingColor.get(part.wing) ?? BUILDING_COLOR) : BUILDING_COLOR,
          // Darker the higher it rises: a one-floor shed next to an eight-floor core should look it.
          opacity: 0.22 + Math.min(part.floors, 8) * 0.07,
          wing: part.wing ?? null,
          floors: part.floors,
        };
      });
      const detail = this.details().get(building.code);
      let rooms: MappedBuilding['rooms'] = [];
      let roomPoints: MapPoint[] = [];
      if (building.placement && detail) {
        const placement = placementForFloor(building.placement, detail.top, detail.width, detail.height);
        const drawn = detail.spaces.filter((s) => s.shape && s.shape.length > 2);
        const shapes = drawn.map((s) => s.shape!.map((p) => this.toMap(drawingToGround(placement, p))));
        rooms = drawn.map((s, i) => ({ d: areaPath(shapes[i]), fill: CATEGORY_COLORS[s.category ?? 'OTHER'] }));
        roomPoints = shapes.flat();
      }
      if (!parts.length && !roomPoints.length) continue;
      // The label on the building's biggest part, or on its rooms when the cadastre has none.
      const biggest = [...parts].sort((a, b) => area(b.points) - area(a.points))[0]?.points ?? roomPoints;
      const floorCount = Math.max(0, ...parts.map((p) => p.floors), ...building.floors.map((f) => Math.round(f.level)));
      out.push({
        code: building.code,
        name: building.name,
        floor: groundFloor(building)?.code ?? null,
        floors: floorCount,
        parts: parts.map(({ d, fill, opacity, wing }) => ({ d, fill, opacity, wing })),
        rooms,
        label: middle(biggest),
      });
    }
    return out.sort((a, b) => a.code.localeCompare(b.code));
  });

  readonly unmapped = computed(() => this.all().filter((b) => !this.mapped().some((m) => m.code === b.code)));

  /** The wings the map colours, named after their buildings. */
  readonly wingKey = computed(() => {
    const key: { code: string; label: string; color: string }[] = [];
    for (const building of this.all()) {
      const colors = wingColors(building);
      const used = new Set((building.footprint ?? []).map((p) => p.wing).filter((w): w is string => !!w));
      for (const wing of building.wings) {
        if (!used.has(wing.code)) continue;
        key.push({ code: `${building.code}-${wing.code}`, label: `${building.code} · ${wing.name}`, color: colors.get(wing.code)! });
      }
    }
    return key;
  });

  readonly drawn = computed(() => {
    const ground = this.ground();
    if (!ground || !this.centre()) return null;
    const area = (rings: Coordinate[][]) => rings.map((r) => r.map((c) => this.toMap(c)));
    const blocks = area(ground.blocks);
    // The view: the buildings with a block round them, or the whole ground.
    const focus = this.mapped().length ? this.mapped().map((b) => b.label) : blocks.flat();
    const margin = this.mapped().length ? 90 : 20;
    const xs = focus.map((p) => p.x);
    const ys = focus.map((p) => p.y);
    const box: Box = {
      x: Math.min(...xs) - margin,
      y: Math.min(...ys) - margin,
      width: Math.max(...xs) - Math.min(...xs) + 2 * margin,
      height: Math.max(...ys) - Math.min(...ys) + 2 * margin,
    };
    const minLength = 60 / ZOOMS[this.zoom()];
    const labels: { name: string; x: number; y: number; angle: number }[] = [];
    const seen = new Set<string>();
    for (const street of ground.streets) {
      const at = streetLabel(street.path.map((c) => this.toMap(c)), box, minLength);
      if (at && !seen.has(street.name + Math.round(at.x / 60) + ',' + Math.round(at.y / 60))) {
        seen.add(street.name + Math.round(at.x / 60) + ',' + Math.round(at.y / 60));
        labels.push({ name: street.name, ...at });
      }
    }
    return {
      box,
      blocks: blocks.map(areaPath),
      roadways: area(ground.roadways).map(areaPath),
      medians: area(ground.medians).map(areaPath),
      sidewalks: area(ground.sidewalks).map(areaPath),
      labels,
    };
  });

  setZoom(step: number): void {
    const element = this.scroller()?.nativeElement;
    if (element && element.scrollWidth) {
      this.centreShare = {
        x: (element.scrollLeft + element.clientWidth / 2) / element.scrollWidth,
        y: (element.scrollTop + element.clientHeight / 2) / element.scrollHeight,
      };
    }
    this.zoom.set(Math.max(0, Math.min(ZOOMS.length - 1, step)));
  }
}

/** The floor a building meets the street on: the lowest one at or above ground. */
function groundFloor(building: Building): Floor | null {
  const floors = [...building.floors].sort((a, b) => a.level - b.level);
  return floors.find((f) => f.level >= 0 && f.status !== 'UNMAPPED') ?? floors.find((f) => f.level >= 0) ?? null;
}

/** Each of a building's wings with the colour the map gives it. */
function wingColors(building: Building): Map<string, string> {
  const colors = new Map<string, string>();
  let next = 0;
  for (const wing of building.wings) {
    colors.set(wing.code, WING_COLORS[wing.code] ?? MORE_WING_COLORS[next++ % MORE_WING_COLORS.length]);
  }
  return colors;
}

function area(points: MapPoint[]): number {
  let total = 0;
  for (let i = 0; i < points.length; i++) {
    const a = points[i];
    const b = points[(i + 1) % points.length];
    total += a.x * b.y - b.x * a.y;
  }
  return Math.abs(total) / 2;
}

function middle(points: MapPoint[]): MapPoint {
  const ring = points.length > 1 && points[0].x === points[points.length - 1].x && points[0].y === points[points.length - 1].y
    ? points.slice(0, -1)
    : points;
  return {
    x: ring.reduce((t, p) => t + p.x, 0) / Math.max(1, ring.length),
    y: ring.reduce((t, p) => t + p.y, 0) / Math.max(1, ring.length),
  };
}

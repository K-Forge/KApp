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

interface PlacedBuilding {
  code: string;
  name: string;
  floor: string;
  rooms: { d: string; fill: string }[];
  outline: string;
  label: MapPoint;
}

/** Pixels per metre at each zoom step. */
const ZOOMS = [0.6, 0.9, 1.3, 2, 3, 4.5, 6.5];

/**
 * The campus from above: the city's blocks and streets, and on them every building laid on the
 * ground, drawn from its ground floor.
 *
 * <p>North is up, as on any map. The buildings sit where their placement puts them, so the
 * distance between two of them is the distance on the street - this is the screen that answers
 * "how far is the CPC from the Edificio Central".
 */
@Component({
  selector: 'app-campus-map-page',
  imports: [RouterLink, PageIntroComponent, ApiErrorBannerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        title="Campus map"
        what="The campus from above: the city's blocks and streets, and every building laid on them, in its place and to scale."
        [can]="['See where each building is and how far apart they are', 'Open a building\\'s floors from the map', 'See which buildings are not on the map yet']"
        note="North is up. A building appears once it is laid on the ground - where its drawing's corner is, which way it faces and at what scale. The streets are the city's own reference map."
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
                @for (d of m.blocks; track $index) {
                  <path class="block" [attr.d]="d" />
                }
                @for (d of m.roadways; track $index) {
                  <path class="roadway" [attr.d]="d" />
                }
                @for (d of m.medians; track $index) {
                  <path class="median" [attr.d]="d" />
                }
                @for (d of m.sidewalks; track $index) {
                  <path class="sidewalk" [attr.d]="d" />
                }
                @for (s of m.labels; track $index) {
                  <text class="street" [attr.transform]="'translate(' + s.x + ' ' + s.y + ') rotate(' + s.angle + ')'" [attr.font-size]="11 / zooms[zoom()]">{{ s.name }}</text>
                }
                @for (b of placed(); track b.code) {
                  <a [routerLink]="['/data/floors', b.code, b.floor]" class="building" [attr.aria-label]="b.name + ' - open its floors'">
                    <title>{{ b.name }}</title>
                    <path class="footprint" [attr.d]="b.outline" />
                    @for (room of b.rooms; track $index) {
                      <path class="room" [attr.d]="room.d" [attr.fill]="room.fill" />
                    }
                    <text class="code" [attr.x]="b.label.x" [attr.y]="b.label.y" [attr.font-size]="14 / zooms[zoom()]">{{ b.code }}</text>
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
          <p class="credit">Streets and blocks: {{ ground()?.source }}</p>
        </section>

        @if (unplaced().length) {
          <section class="card">
            <h2 class="h">Not on the map yet</h2>
            <p class="text-muted">
              Drawn, but not laid on the ground: nobody has said yet where their drawing's corner is, which
              way it faces and at what scale.
            </p>
            <ul class="unplaced">
              @for (b of unplaced(); track b.code) {
                <li><strong>{{ b.code }}</strong> {{ b.name }}</li>
              }
            </ul>
          </section>
        }
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
    .footprint {
      fill: var(--nav-active-bg);
      stroke: var(--nav-active-edge);
      stroke-width: 2;
      vector-effect: non-scaling-stroke;
    }
    .room {
      stroke: rgb(28 33 40 / 45%);
      stroke-width: 0.5;
      vector-effect: non-scaling-stroke;
    }
    .code {
      fill: var(--nav-active-text);
      font-weight: 800;
      text-anchor: middle;
      dominant-baseline: central;
      paint-order: stroke;
      stroke: var(--bg-elevated);
      stroke-width: 4;
      vector-effect: non-scaling-stroke;
    }
    .building {
      cursor: pointer;
    }
    .building:hover .footprint,
    .building:focus-visible .footprint {
      stroke-width: 3.5;
    }
    .north circle {
      fill: var(--bg-elevated);
      stroke: color-mix(in srgb, var(--text) 30%, transparent);
    }
    .north path,
    .north text {
      fill: var(--text);
    }
    .credit {
      margin: 0.5rem 0 0;
      font-size: 0.6875rem;
      color: var(--text-faint);
    }
    .h {
      font-size: 1rem;
    }
    .unplaced {
      margin: 0;
      padding-left: 1.1rem;
    }
  `,
})
export class CampusMapPage {
  private readonly buildings = inject(BuildingsService);
  private readonly floors = inject(FloorsService);
  private readonly grounds = inject(GroundService);

  readonly zooms = ZOOMS;
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
          const placed = onCampus.filter((b) => b.placement && groundFloor(b));
          const floors = placed.length
            ? forkJoin(placed.map((b) => this.floors.get(b.code, groundFloor(b)!.code)))
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

  /** The middle of the map: the placed buildings' middle, or the ground's. */
  private readonly centre = computed<GeoPoint | null>(() => {
    const origins = this.all().flatMap((b) => (b.placement ? [b.placement.origin] : []));
    if (origins.length) {
      return {
        lat: origins.reduce((t, o) => t + o.lat, 0) / origins.length,
        lon: origins.reduce((t, o) => t + o.lon, 0) / origins.length,
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

  readonly placed = computed<PlacedBuilding[]>(() => {
    if (!this.centre()) return [];
    const out: PlacedBuilding[] = [];
    for (const building of this.all()) {
      const detail = this.details().get(building.code);
      if (!building.placement || !detail) continue;
      const placement = placementForFloor(building.placement, detail.top, detail.width, detail.height);
      const rooms = detail.spaces.filter((s) => s.shape && s.shape.length > 2);
      const onMap = rooms.map((s) => s.shape!.map((p) => this.toMap(drawingToGround(placement, p))));
      const all = onMap.flat();
      if (!all.length) continue;
      out.push({
        code: building.code,
        name: building.name,
        floor: detail.code,
        rooms: rooms.map((s, i) => ({ d: areaPath(onMap[i]), fill: CATEGORY_COLORS[s.category ?? 'OTHER'] })),
        outline: areaPath(hull(all)),
        label: { x: all.reduce((t, p) => t + p.x, 0) / all.length, y: all.reduce((t, p) => t + p.y, 0) / all.length },
      });
    }
    return out;
  });

  readonly unplaced = computed(() => this.all().filter((b) => !this.placed().some((p) => p.code === b.code)));

  readonly drawn = computed(() => {
    const ground = this.ground();
    if (!ground || !this.centre()) return null;
    const area = (rings: Coordinate[][]) => rings.map((r) => r.map((c) => this.toMap(c)));
    const blocks = area(ground.blocks);
    // The view: the placed buildings with a block around them, or the whole ground.
    const focus = this.placed().length ? this.placed().map((b) => b.label) : blocks.flat();
    const margin = this.placed().length ? 170 : 20;
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

/** The floor a building meets the street on: the lowest one at or above ground with rooms drawn. */
function groundFloor(building: Building): Floor | null {
  const floors = [...building.floors].sort((a, b) => a.level - b.level);
  return floors.find((f) => f.level >= 0 && f.status !== 'UNMAPPED') ?? floors.find((f) => f.level >= 0) ?? null;
}

/** The convex hull around a building's rooms: its outline from above, near enough. */
function hull(points: MapPoint[]): MapPoint[] {
  const sorted = [...points].sort((a, b) => a.x - b.x || a.y - b.y);
  const cross = (o: MapPoint, a: MapPoint, b: MapPoint) => (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
  const lower: MapPoint[] = [];
  for (const p of sorted) {
    while (lower.length >= 2 && cross(lower[lower.length - 2], lower[lower.length - 1], p) <= 0) lower.pop();
    lower.push(p);
  }
  const upper: MapPoint[] = [];
  for (const p of sorted.reverse()) {
    while (upper.length >= 2 && cross(upper[upper.length - 2], upper[upper.length - 1], p) <= 0) upper.pop();
    upper.push(p);
  }
  return [...lower.slice(0, -1), ...upper.slice(0, -1)];
}

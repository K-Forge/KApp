import type { Compass, FootprintPart, GeoPoint, Placement, Point } from '../buildings/building.model';
import type { Coordinate, Ground } from './ground.model';

/**
 * Between a building's drawing and the ground, through its placement.
 *
 * <p>A campus is a few hundred metres across, small enough for the plane through a point near it
 * to stand in for the earth: a degree of latitude is 110 574 m there, one of longitude that times
 * the cosine of the latitude. The contract states the same formulas, so every client lays a
 * drawing in the same place.
 */

const METRES_PER_DEGREE_LAT = 110574;
const METRES_PER_DEGREE_LON = 111320;
const RAD = Math.PI / 180;

const COMPASS: readonly Compass[] = ['NORTH', 'EAST', 'SOUTH', 'WEST'];

/** A box in some plane's units. */
export interface Box {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** What is drawn under a floor, in the floor's own units. */
export interface Surroundings {
  blocks: Point[][];
  sidewalks: Point[][];
  roadways: Point[][];
  medians: Point[][];
  streets: { name: string; label: string; path: Point[] }[];
  /**
   * The building's parts as the cadastre records them from above: those that rise to the floor
   * drawn (`reaches`), and those that stop at the floor below it. The rest are not this floor's
   * business, and a part the cadastre does not record is not the cadastre's.
   */
  footprint: { outline: Point[]; reaches: boolean }[];
}

/** One wing's outline on a floor, in the floor's units. */
export interface Margin {
  /** The wing's code; null for the parts of a building that has no wings. */
  wing: string | null;
  outline: Point[];
}

/** Whether a part of `floors` floors and `basements` basements reaches the floor at `level`. */
export function reaches(part: Pick<FootprintPart, 'floors' | 'basements' | 'lowestFloor'>, level: number): boolean {
  if (level < 0) return part.basements >= -Math.floor(level);
  // A part the upper floors carry out over the street is in none of the floors below it.
  const floor = Math.max(1, Math.ceil(level));
  return part.floors >= floor && (part.lowestFloor ?? 1) <= floor;
}

/** Metres east and north of `origin`. */
export function toMetres(origin: GeoPoint, c: Coordinate): { east: number; north: number } {
  return {
    east: (c[0] - origin.lon) * METRES_PER_DEGREE_LON * Math.cos(origin.lat * RAD),
    north: (c[1] - origin.lat) * METRES_PER_DEGREE_LAT,
  };
}

/** The point `east` and `north` metres from `origin`. */
export function fromMetres(origin: GeoPoint, east: number, north: number): Coordinate {
  return [
    origin.lon + east / (METRES_PER_DEGREE_LON * Math.cos(origin.lat * RAD)),
    origin.lat + north / METRES_PER_DEGREE_LAT,
  ];
}

/** Where a point of the drawing lies on the ground. */
export function drawingToGround(placement: Placement, p: Point): Coordinate {
  const b = placement.bearing * RAD;
  const m = placement.metresPerUnit;
  const east = m * (p.x * Math.cos(b) - p.y * Math.sin(b));
  const north = -m * (p.x * Math.sin(b) + p.y * Math.cos(b));
  return fromMetres(placement.origin, east, north);
}

/** Where a point on the ground falls on the drawing: `drawingToGround` undone. */
export function groundToDrawing(placement: Placement, c: Coordinate): Point {
  const b = placement.bearing * RAD;
  const m = placement.metresPerUnit;
  const { east, north } = toMetres(placement.origin, c);
  const u = east / m;
  const v = -north / m;
  return { x: u * Math.cos(b) + v * Math.sin(b), y: -u * Math.sin(b) + v * Math.cos(b) };
}

/** The quarter the bearing is nearest: what a floor drawn in the placement's frame has as `top`. */
export function nearestQuarter(bearing: number): Compass {
  return COMPASS[Math.round((((bearing % 360) + 360) % 360) / 90) % 4];
}

/**
 * The placement of one floor's drawing as it stands now.
 *
 * <p>A building is laid on the ground once, for its drawing as its floors share it. A floor since
 * turned in the editor - by quarters, which is all turning does - has its own top: its placement is
 * the building's turned the same number of quarters. Each clockwise quarter takes 90 degrees off
 * the bearing and brings the old bottom-left corner to the origin. `width` and `height` are the
 * floor's as it stands; a floor whose top nobody set is taken as drawn in the building's frame.
 */
export function placementForFloor(building: Placement, top: Compass | null | undefined, width: number, height: number): Placement {
  if (!top) return building;
  const quarters = (((COMPASS.indexOf(nearestQuarter(building.bearing)) - COMPASS.indexOf(top)) % 4) + 4) % 4;
  let placement = building;
  // The size before turning: an odd number of quarters swapped it.
  let w = quarters % 2 ? height : width;
  let h = quarters % 2 ? width : height;
  for (let i = 0; i < quarters; i++) {
    const [lon, lat] = drawingToGround(placement, { x: 0, y: h });
    placement = { ...placement, origin: { lat, lon }, bearing: (((placement.bearing - 90) % 360) + 360) % 360 };
    [w, h] = [h, w];
  }
  return placement;
}

/** The part of the ground within `view`, in the drawing's units, and the building's own parts. */
export function surroundings(
  ground: Ground,
  placement: Placement,
  view: Box,
  footprint: readonly FootprintPart[] = [],
  level = 1,
  below = level - 1,
): Surroundings {
  const toDrawing = (ring: Coordinate[]) => ring.map((c) => groundToDrawing(placement, c));
  const within = (points: Point[]) => {
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
    for (const p of points) {
      x0 = Math.min(x0, p.x); y0 = Math.min(y0, p.y); x1 = Math.max(x1, p.x); y1 = Math.max(y1, p.y);
    }
    return x1 >= view.x && y1 >= view.y && x0 <= view.x + view.width && y0 <= view.y + view.height;
  };
  const areas = (rings: Coordinate[][]) => rings.map(toDrawing).filter(within);
  return {
    blocks: areas(ground.blocks),
    sidewalks: areas(ground.sidewalks),
    roadways: areas(ground.roadways),
    medians: areas(ground.medians),
    streets: ground.streets
      .map((s) => ({ name: s.name, label: s.label, path: toDrawing(s.path) }))
      .filter((s) => within(s.path)),
    footprint: footprint
      .filter((part) => part.lot && (reaches(part, level) || (level > 0 && reaches(part, below))))
      .map((part) => ({ outline: toDrawing(part.ring), reaches: reaches(part, level) })),
  };
}

/**
 * Where a floor's rooms go: for each wing, the outline of its parts that rise to the floor, the
 * cadastre's and those surveyed on site alike.
 */
export function margins(footprint: readonly FootprintPart[], placement: Placement, level: number): Margin[] {
  const wings = new Map<string | null, Coordinate[][]>();
  for (const part of footprint) {
    if (!reaches(part, level)) continue;
    const wing = part.wing ?? null;
    wings.set(wing, [...(wings.get(wing) ?? []), part.ring]);
  }
  return [...wings].flatMap(([wing, rings]) =>
    outlineOf(rings).map((ring) => ({ wing, outline: ring.map((c) => groundToDrawing(placement, c)) })),
  );
}

/**
 * The outline of several parts taken as one. The cadastre draws the parts of a lot as a
 * partition - neighbours share their corners - so every edge two parts share cancels out and the
 * edges left are the outside. A corner of one part that lies on another's edge is put on that
 * edge first, or a part beside two others would not cancel.
 */
export function outlineOf(rings: readonly Coordinate[][]): Coordinate[][] {
  const polygons = rings.map(openRing).filter((r) => r.length >= 3).map(counterClockwise);
  const corners = polygons.flat();
  const key = (a: Coordinate, b: Coordinate) => `${a[0]},${a[1]}>${b[0]},${b[1]}`;
  const edges = new Map<string, { from: Coordinate; to: Coordinate; count: number }>();
  for (const polygon of polygons) {
    polygon.forEach((a, i) => {
      const b = polygon[(i + 1) % polygon.length];
      const stops = [a, ...onSegment(a, b, corners), b];
      for (let j = 1; j < stops.length; j++) {
        const [p, q] = [stops[j - 1], stops[j]];
        const back = edges.get(key(q, p));
        if (back) {
          if (--back.count === 0) edges.delete(key(q, p));
          continue;
        }
        const same = edges.get(key(p, q));
        if (same) same.count++;
        else edges.set(key(p, q), { from: p, to: q, count: 1 });
      }
    });
  }
  // An edge two overlapping parts both walk the same way is left as many times as it was walked:
  // counted once, the walk below runs out of edges at its far end and closes the outline with a
  // wall that is not there.
  const leaving = new Map<string, Coordinate[]>();
  for (const { from, to, count } of edges.values()) {
    const at = `${from[0]},${from[1]}`;
    leaving.set(at, [...(leaving.get(at) ?? []), ...Array<Coordinate>(count).fill(to)]);
  }
  const outlines: Coordinate[][] = [];
  for (const [start, next] of leaving) {
    while (next.length) {
      const first = next.pop()!;
      const ring: Coordinate[] = [start.split(',').map(Number) as Coordinate];
      let at = first;
      for (let guard = 0; `${at[0]},${at[1]}` !== start && guard < 100000; guard++) {
        ring.push(at);
        const onward = leaving.get(`${at[0]},${at[1]}`);
        const step = onward?.pop();
        if (!step) break;
        at = step;
      }
      if (ring.length >= 3) outlines.push(ring);
    }
  }
  return outlines;
}

/** A ring without the corner that closes it. */
function openRing(ring: Coordinate[]): Coordinate[] {
  const [first, last] = [ring[0], ring[ring.length - 1]];
  return ring.length > 1 && first[0] === last[0] && first[1] === last[1] ? ring.slice(0, -1) : [...ring];
}

function counterClockwise(ring: Coordinate[]): Coordinate[] {
  let twice = 0;
  ring.forEach((a, i) => {
    const b = ring[(i + 1) % ring.length];
    twice += a[0] * b[1] - b[0] * a[1];
  });
  return twice < 0 ? [...ring].reverse() : ring;
}

/** The corners lying on segment a-b, strictly between its ends, in order from a. */
function onSegment(a: Coordinate, b: Coordinate, corners: readonly Coordinate[]): Coordinate[] {
  const [dx, dy] = [b[0] - a[0], b[1] - a[1]];
  const length2 = dx * dx + dy * dy;
  if (!length2) return [];
  // A centimetre, in degrees: the cadastre's corners are given to a tenth of a millimetre.
  const tolerance = 1e-7;
  const found: { t: number; c: Coordinate }[] = [];
  for (const c of corners) {
    const t = ((c[0] - a[0]) * dx + (c[1] - a[1]) * dy) / length2;
    if (t <= 1e-9 || t >= 1 - 1e-9) continue;
    const off = Math.abs((c[0] - a[0]) * dy - (c[1] - a[1]) * dx) / Math.sqrt(length2);
    if (off <= tolerance && !found.some((f) => f.c[0] === c[0] && f.c[1] === c[1])) found.push({ t, c });
  }
  return found.sort((p, q) => p.t - q.t).map((f) => f.c);
}

/**
 * Where to write a street's name inside `view`: the middle of the longest stretch of its axis
 * that is inside, turned along it and never upside down. Null when too little of it shows.
 */
export function streetLabel(path: Point[], view: Box, minLength: number): { x: number; y: number; angle: number } | null {
  let best: { length: number; x: number; y: number; angle: number } | null = null;
  const inside = (p: Point) => p.x >= view.x && p.y >= view.y && p.x <= view.x + view.width && p.y <= view.y + view.height;
  for (let i = 1; i < path.length; i++) {
    const a = path[i - 1];
    const b = path[i];
    const mid = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
    if (!inside(mid)) continue;
    const length = Math.hypot(b.x - a.x, b.y - a.y);
    if (length < minLength || (best && best.length >= length)) continue;
    let angle = (Math.atan2(b.y - a.y, b.x - a.x) * 180) / Math.PI;
    if (angle > 90) angle -= 180;
    if (angle < -90) angle += 180;
    best = { length, x: mid.x, y: mid.y, angle };
  }
  return best && { x: best.x, y: best.y, angle: best.angle };
}

/** An area as an SVG path. */
export function areaPath(points: Point[]): string {
  return points.length ? 'M' + points.map((p) => `${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join('L') + 'Z' : '';
}

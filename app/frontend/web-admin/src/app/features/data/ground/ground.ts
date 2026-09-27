import type { Compass, GeoPoint, Placement, Point } from '../buildings/building.model';
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

/** The part of the ground within `view`, in the drawing's units. */
export function surroundings(ground: Ground, placement: Placement, view: Box): Surroundings {
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
  };
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

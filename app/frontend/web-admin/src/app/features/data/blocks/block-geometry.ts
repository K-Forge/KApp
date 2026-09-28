import type { GeoPoint } from '../buildings/building.model';
import type { Coordinate } from '../ground/ground.model';
import { fromMetres, toMetres } from '../ground/ground';

/**
 * The block editor's geometry: a block drawn in metres, turned so that the buildings' walls run
 * across and down the screen, and the edits made on it - pure functions, so they can be tested
 * without a screen.
 */

/** A point on the editor's screen, in metres: x to the right, y down. */
export interface ViewPoint {
  x: number;
  y: number;
}

/**
 * How the block lies on the screen: the point at the screen's origin, and the compass direction,
 * in degrees, the top of the screen faces - the direction of a building's walls, so that they run
 * level and plumb.
 */
export interface Frame {
  origin: GeoPoint;
  up: number;
}

const RAD = Math.PI / 180;

export function toView(frame: Frame, c: Coordinate): ViewPoint {
  const { east, north } = toMetres(frame.origin, c);
  const u = frame.up * RAD;
  return { x: east * Math.cos(u) - north * Math.sin(u), y: -east * Math.sin(u) - north * Math.cos(u) };
}

/** The point on the earth under a point of the screen, to a centimetre (7 decimals of a degree). */
export function fromView(frame: Frame, p: ViewPoint): Coordinate {
  const u = frame.up * RAD;
  const east = p.x * Math.cos(u) - p.y * Math.sin(u);
  const north = -p.x * Math.sin(u) - p.y * Math.cos(u);
  const [lon, lat] = fromMetres(frame.origin, east, north);
  return [round7(lon), round7(lat)];
}

/** The outline's corners, without the first one repeated at the end. */
export function openRing(ring: readonly Coordinate[]): Coordinate[] {
  const last = ring[ring.length - 1];
  return ring.length > 1 && last[0] === ring[0][0] && last[1] === ring[0][1] ? ring.slice(0, -1) : [...ring];
}

/** The outline closed, as the API takes it: the first corner repeated at the end. */
export function closeRing(points: readonly Coordinate[]): Coordinate[] {
  return [...points, points[0]];
}

export function translate(shape: readonly ViewPoint[], dx: number, dy: number): ViewPoint[] {
  return shape.map((p) => ({ x: p.x + dx, y: p.y + dy }));
}

export function withVertex(shape: readonly ViewPoint[], index: number, to: ViewPoint): ViewPoint[] {
  return shape.map((p, i) => (i === index ? to : p));
}

/**
 * The edge from corner `edge` to the next moved across itself, parallel, until it passes through
 * `to`: its two corners go together, so a rectangle stays a rectangle - how a wall is pushed out.
 */
export function withEdgeMoved(shape: readonly ViewPoint[], edge: number, to: ViewPoint): ViewPoint[] {
  const a = shape[edge];
  const b = shape[(edge + 1) % shape.length];
  const length = Math.hypot(b.x - a.x, b.y - a.y);
  if (length === 0) return [...shape];
  const nx = -(b.y - a.y) / length;
  const ny = (b.x - a.x) / length;
  const by = (to.x - a.x) * nx + (to.y - a.y) * ny;
  return shape.map((p, i) => (i === edge || i === (edge + 1) % shape.length ? { x: p.x + by * nx, y: p.y + by * ny } : p));
}

/** A corner added on the edge from corner `edge` to the next, at `at`. */
export function withInsertedVertex(shape: readonly ViewPoint[], edge: number, at: ViewPoint): ViewPoint[] {
  return [...shape.slice(0, edge + 1), at, ...shape.slice(edge + 1)];
}

/** The shape without one corner, or null when it would be left with fewer than three. */
export function withoutVertex(shape: readonly ViewPoint[], index: number): ViewPoint[] | null {
  return shape.length <= 3 ? null : shape.filter((_, i) => i !== index);
}

/** The rectangle round the shape, square to the screen: a slanted or ragged outline made square. */
export function squared(shape: readonly ViewPoint[]): ViewPoint[] {
  const xs = shape.map((p) => p.x);
  const ys = shape.map((p) => p.y);
  const x0 = Math.min(...xs);
  const x1 = Math.max(...xs);
  const y0 = Math.min(...ys);
  const y1 = Math.max(...ys);
  return [
    { x: x0, y: y0 },
    { x: x1, y: y0 },
    { x: x1, y: y1 },
    { x: x0, y: y1 },
  ];
}

/** A rectangle `width` by `height` metres round `centre`. */
export function rectangleAt(centre: ViewPoint, width: number, height: number): ViewPoint[] {
  return squared([
    { x: centre.x - width / 2, y: centre.y - height / 2 },
    { x: centre.x + width / 2, y: centre.y + height / 2 },
  ]);
}

/** Square metres. */
export function areaOf(shape: readonly ViewPoint[]): number {
  let total = 0;
  for (let i = 0; i < shape.length; i++) {
    const a = shape[i];
    const b = shape[(i + 1) % shape.length];
    total += a.x * b.y - b.x * a.y;
  }
  return Math.abs(total) / 2;
}

/** Where a label goes: the middle of the corners. */
export function middleOf(shape: readonly ViewPoint[]): ViewPoint {
  return {
    x: shape.reduce((t, p) => t + p.x, 0) / Math.max(1, shape.length),
    y: shape.reduce((t, p) => t + p.y, 0) / Math.max(1, shape.length),
  };
}

/**
 * `p` lined up with the nearest of `targets` across and down, each within `reach` metres, and
 * otherwise rounded to `step`: a corner dragged near another's line lands on it.
 */
export function snapped(p: ViewPoint, targets: readonly ViewPoint[], reach: number, step = 0.05): ViewPoint {
  let x = Math.round(p.x / step) * step;
  let y = Math.round(p.y / step) * step;
  let bestX = reach;
  let bestY = reach;
  for (const q of targets) {
    if (Math.abs(q.x - p.x) < bestX) {
      bestX = Math.abs(q.x - p.x);
      x = q.x;
    }
    if (Math.abs(q.y - p.y) < bestY) {
      bestY = Math.abs(q.y - p.y);
      y = q.y;
    }
  }
  return { x, y };
}

/** The nudge that lines a moved shape's corners up with others', each way within `reach` metres. */
export function alignment(shape: readonly ViewPoint[], targets: readonly ViewPoint[], reach: number): [number, number] {
  let dx = 0;
  let dy = 0;
  let bestX = reach;
  let bestY = reach;
  for (const p of shape) {
    for (const q of targets) {
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

/** SVG path data for a closed shape. */
export function pathOf(shape: readonly ViewPoint[]): string {
  return shape.length ? 'M' + shape.map((p) => `${p.x.toFixed(2)} ${p.y.toFixed(2)}`).join(' L') + ' Z' : '';
}

/** The block a lot is on: the first nine digits of its code. */
export function blockOf(lot: string | null | undefined): string | null {
  return lot && lot.length >= 9 ? lot.slice(0, 9) : null;
}

function round7(v: number): number {
  return Math.round(v * 1e7) / 1e7;
}

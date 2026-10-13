import type { Accessibility, Compass, Corridor, FloorStatus, Point } from '../buildings/building.model';
import type { Door, SpaceCategory } from '../spaces/space.model';
import type { FloorDetail, FloorLayoutRequest, LayoutSpace } from './floor.model';
import { t } from '../../../core/i18n/i18n.service';

/**
 * A floor while somebody is editing it: everything a layout save sends, plus a local key per
 * space. The key is what the editor selects by - the internal code is editable, so it cannot be.
 *
 * Every function here is pure and returns a new draft. The page keeps the old ones for undo, and
 * the draft is what gets written to the device between saves.
 *
 * The floor is a drawing in its own units - width by height, origin at the top-left corner as the
 * plan hangs - and each placed space is a polygon on it, traced from the evacuation plan.
 */
export interface DraftSpace extends LayoutSpace {
  key: string;
}

export interface FloorDraft {
  width: number;
  height: number;
  /** The direction on the ground the drawing's top edge faces; null until somebody says. */
  top: Compass | null;
  outline: Point[];
  status: FloorStatus;
  accessibility: Accessibility;
  note: string;
  corridors: Corridor[];
  spaces: DraftSpace[];
}

/** A rectangle on the drawing: what dragging out a new room gives. */
export interface Box {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** The type a freshly drawn box gets until somebody says what it is. */
export const UNIDENTIFIED_TYPE = 'OTHER';
export const UNIDENTIFIED_NAME = 'Sin identificar';

const CODE = /^[A-Za-z0-9][A-Za-z0-9-]*$/;
const COLOR = /^#[0-9A-Fa-f]{6}$/;
export const MAX_SPACES = 500;
export const MAX_SIZE = 20000;

let counter = 0;

/**
 * A key nobody else on this device has. Not crypto.randomUUID: the portal is also opened from an
 * iPad over plain http on the local network, and outside a secure context that function is absent.
 */
export function newKey(): string {
  counter += 1;
  return `k${Date.now().toString(36)}${counter.toString(36)}`;
}

const copy = (points: Point[]): Point[] => points.map((p) => ({ x: p.x, y: p.y }));
const copyDoors = (doors: Door[]): Door[] => doors.map((d) => ({ from: { ...d.from }, to: { ...d.to } }));

export function fromDetail(detail: FloorDetail): FloorDraft {
  return {
    width: detail.width,
    height: detail.height,
    top: detail.top ?? null,
    outline: copy(detail.outline ?? []),
    status: detail.status,
    accessibility: detail.accessibility,
    note: detail.note ?? '',
    corridors: (detail.corridors ?? []).map((c) => ({ ...c, path: copy(c.path) })),
    spaces: detail.spaces.map((s) => ({
      // Keyed by the code as loaded, so a selection survives a save and a reload.
      key: `c:${s.code}`,
      code: s.code,
      doorCode: s.doorCode ?? null,
      wing: s.wing ?? null,
      name: s.name,
      typeCode: s.typeCode,
      aliases: [...(s.aliases ?? [])],
      shape: s.shape?.length ? copy(s.shape) : null,
      doors: copyDoors(s.doors ?? []),
      accessVia: s.accessVia ?? null,
      accessibility: s.accessibility ?? null,
      note: s.note ?? null,
      capacity: s.capacity ?? null,
    })),
  };
}

/** What a layout save sends. Empty text is sent as absent, never as an empty value. */
export function toRequest(draft: FloorDraft, version: number): FloorLayoutRequest {
  return {
    version,
    width: draft.width,
    height: draft.height,
    top: draft.top ?? null,
    outline: draft.outline,
    status: draft.status,
    accessibility: draft.accessibility,
    note: blankToNull(draft.note),
    corridors: draft.corridors.map((c) => ({ code: c.code.trim(), name: c.name.trim(), color: c.color, path: c.path })),
    spaces: draft.spaces.map((s) => {
      const placed = isPlaced(s);
      return {
        code: s.code.trim(),
        doorCode: blankToNull(s.doorCode),
        wing: blankToNull(s.wing),
        name: s.name.trim(),
        typeCode: s.typeCode,
        aliases: s.aliases.map((a) => a.trim()).filter(Boolean),
        shape: placed ? s.shape : null,
        doors: placed ? s.doors : [],
        accessVia: blankToNull(s.accessVia),
        accessibility: s.accessibility ?? null,
        note: blankToNull(s.note),
        capacity: s.capacity ?? null,
      };
    }),
  };
}

/**
 * A short fingerprint of what a draft would save - FNV-1a over the request - so a draft kept on
 * the device can tell whether the drawing it started from is still the one on the server.
 */
export function fingerprint(draft: FloorDraft): string {
  const text = JSON.stringify(toRequest(draft, 0));
  let hash = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) {
    hash ^= text.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(16).padStart(8, '0');
}

/** True when the two drafts would save the same floor. */
export function sameFloor(a: FloorDraft, b: FloorDraft): boolean {
  return JSON.stringify(toRequest(a, 0)) === JSON.stringify(toRequest(b, 0));
}

export function isPlaced(space: LayoutSpace): space is LayoutSpace & { shape: Point[] } {
  return !!space.shape && space.shape.length >= 3;
}

// ── Which way the drawing faces ───────────────────────────────────────────────────────────

/** The directions in clockwise order, as a compass reads. */
export const COMPASS: readonly Compass[] = ['NORTH', 'EAST', 'SOUTH', 'WEST'];

/**
 * The drawing turned a quarter at a time - clockwise for a positive count - with everything on
 * it: rooms, doors, corridors, the outline, and the direction its top faces, since turning it
 * clockwise brings what was on its left to the top.
 */
export function turn(draft: FloorDraft, quarters: number): FloorDraft {
  let next = draft;
  for (let i = 0; i < ((quarters % 4) + 4) % 4; i++) next = quarterTurn(next);
  return next;
}

function quarterTurn(draft: FloorDraft): FloorDraft {
  // Clockwise: the left edge becomes the top, the top edge the right.
  const point = (p: Point): Point => ({ x: draft.height - p.y, y: p.x });
  const facing = draft.top ? COMPASS.indexOf(draft.top) : -1;
  return {
    ...draft,
    width: draft.height,
    height: draft.width,
    top: facing < 0 ? null : COMPASS[(facing + 3) % 4],
    outline: draft.outline.map(point),
    corridors: draft.corridors.map((c) => ({ ...c, path: c.path.map(point) })),
    spaces: draft.spaces.map((s) => ({
      ...s,
      shape: s.shape ? s.shape.map(point) : s.shape,
      doors: s.doors.map((d) => ({ from: point(d.from), to: point(d.to) })),
    })),
  };
}

/** The drawing turned so `direction` is at the top. Unchanged while nobody has said which way it faces. */
export function turnUp(draft: FloorDraft, direction: Compass): FloorDraft {
  if (!draft.top) return draft;
  return turn(draft, COMPASS.indexOf(draft.top) - COMPASS.indexOf(direction));
}

/** Where north lies on the drawing, in degrees clockwise from its top; null while it is not known. */
export function northAngle(top: Compass | null | undefined): number | null {
  return top ? (360 - COMPASS.indexOf(top) * 90) % 360 : null;
}

// ── Geometry ──────────────────────────────────────────────────────────────────────────────

export function rectangle(box: Box): Point[] {
  const { x, y, width, height } = box;
  return [
    { x, y },
    { x: x + width, y },
    { x: x + width, y: y + height },
    { x, y: y + height },
  ];
}

export function boundsOf(points: Point[]): Box {
  const xs = points.map((p) => p.x);
  const ys = points.map((p) => p.y);
  const x = Math.min(...xs);
  const y = Math.min(...ys);
  return { x, y, width: Math.max(...xs) - x, height: Math.max(...ys) - y };
}

export function area(points: Point[]): number {
  let twice = 0;
  for (let i = 0; i < points.length; i++) {
    const a = points[i];
    const b = points[(i + 1) % points.length];
    twice += a.x * b.y - b.x * a.y;
  }
  return Math.abs(twice) / 2;
}

/** Where a room's name goes: its centroid, or the middle of its box when that falls outside. */
export function labelPoint(points: Point[]): Point {
  let cx = 0;
  let cy = 0;
  let twice = 0;
  for (let i = 0; i < points.length; i++) {
    const a = points[i];
    const b = points[(i + 1) % points.length];
    const product = a.x * b.y - b.x * a.y;
    twice += product;
    cx += (a.x + b.x) * product;
    cy += (a.y + b.y) * product;
  }
  const box = boundsOf(points);
  const middle = { x: box.x + box.width / 2, y: box.y + box.height / 2 };
  if (twice === 0) return middle;
  const centroid = { x: cx / (3 * twice), y: cy / (3 * twice) };
  return inside(points, centroid) ? centroid : middle;
}

export function translate(points: Point[], dx: number, dy: number): Point[] {
  return points.map((p) => ({ x: p.x + dx, y: p.y + dy }));
}

export function within(points: Point[], width: number, height: number): boolean {
  return points.every((p) => p.x >= 0 && p.y >= 0 && p.x <= width && p.y <= height);
}

/** Strictly inside: a point on the outline is not. */
export function inside(points: Point[], p: Point): boolean {
  if (onOutline(points, p, 0.001)) return false;
  let hit = false;
  for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
    const a = points[i];
    const b = points[j];
    if (a.y > p.y !== b.y > p.y && p.x < ((b.x - a.x) * (p.y - a.y)) / (b.y - a.y) + a.x) hit = !hit;
  }
  return hit;
}

function cross(o: Point, a: Point, b: Point): number {
  return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
}

/** Whether two segments cross at a point inside both - touching or running along a wall is not. */
function properlyCross(a: Point, b: Point, c: Point, d: Point): boolean {
  const d1 = cross(c, d, a);
  const d2 = cross(c, d, b);
  const d3 = cross(a, b, c);
  const d4 = cross(a, b, d);
  return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
}

function meet(a: Point, b: Point, c: Point, d: Point): boolean {
  return (
    properlyCross(a, b, c, d) ||
    distanceToSegment(c, a, b) < 0.001 ||
    distanceToSegment(d, a, b) < 0.001 ||
    distanceToSegment(a, c, d) < 0.001 ||
    distanceToSegment(b, c, d) < 0.001
  );
}

/** No two edges that are not neighbours meet: the outline has one inside a client can fill. */
export function simple(points: Point[]): boolean {
  const n = points.length;
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      if (j === i + 1 || (i === 0 && j === n - 1)) continue;
      if (meet(points[i], points[(i + 1) % n], points[j], points[(j + 1) % n])) return false;
    }
  }
  return true;
}

/**
 * Whether two rooms cover some of the same floor. Sharing a wall does not count: their edges may
 * run along each other, but none may cross another, and no corner or middle of one may be inside
 * the other.
 */
export function overlaps(a: Point[], b: Point[]): boolean {
  const ba = boundsOf(a);
  const bb = boundsOf(b);
  if (ba.x >= bb.x + bb.width || bb.x >= ba.x + ba.width || ba.y >= bb.y + bb.height || bb.y >= ba.y + ba.height) {
    return false;
  }
  for (let i = 0; i < a.length; i++) {
    for (let j = 0; j < b.length; j++) {
      if (properlyCross(a[i], a[(i + 1) % a.length], b[j], b[(j + 1) % b.length])) return true;
    }
  }
  const probes = (points: Point[]): Point[] => [
    ...points,
    ...points.map((p, i) => midpoint(p, points[(i + 1) % points.length])),
    labelPoint(points),
  ];
  return probes(a).some((p) => inside(b, p)) || probes(b).some((p) => inside(a, p));
}

function midpoint(a: Point, b: Point): Point {
  return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
}

function projectOnSegment(p: Point, a: Point, b: Point): Point {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const length = dx * dx + dy * dy;
  const t = length === 0 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / length));
  return { x: a.x + t * dx, y: a.y + t * dy };
}

export function distanceToSegment(p: Point, a: Point, b: Point): number {
  const at = projectOnSegment(p, a, b);
  return Math.hypot(p.x - at.x, p.y - at.y);
}

export function onOutline(points: Point[], p: Point, tolerance = 1): boolean {
  return points.some((a, i) => distanceToSegment(p, a, points[(i + 1) % points.length]) <= tolerance);
}

/** The edge of the outline nearest a point, and where on it the point falls. */
export function nearestEdge(points: Point[], p: Point): { index: number; at: Point; distance: number } {
  let best = { index: 0, at: points[0], distance: Infinity };
  points.forEach((a, index) => {
    const at = projectOnSegment(p, a, points[(index + 1) % points.length]);
    const distance = Math.hypot(p.x - at.x, p.y - at.y);
    if (distance < best.distance) best = { index, at, distance };
  });
  return best;
}

/** A door lies on its room's outline, along one edge. */
export function doorOnOutline(points: Point[], door: Door): boolean {
  return points.some((a, i) => {
    const b = points[(i + 1) % points.length];
    return distanceToSegment(door.from, a, b) <= 1 && distanceToSegment(door.to, a, b) <= 1;
  });
}

/** How wide a new door is: about a metre on a plan traced at the usual scale. */
export function doorWidth(draft: FloorDraft): number {
  return Math.max(8, Math.round(Math.min(draft.width, draft.height) / 25));
}

/**
 * A door `width` wide centred where `p` falls on the nearest edge, kept within that edge. Null
 * when the edge is too short to hold one.
 */
export function doorAt(points: Point[], p: Point, width: number): Door | null {
  const { index, at } = nearestEdge(points, p);
  const a = points[index];
  const b = points[(index + 1) % points.length];
  const length = Math.hypot(b.x - a.x, b.y - a.y);
  if (length < 2) return null;
  const half = Math.min(width, length) / 2;
  const ux = (b.x - a.x) / length;
  const uy = (b.y - a.y) / length;
  const along = Math.max(half, Math.min(length - half, (at.x - a.x) * ux + (at.y - a.y) * uy));
  return {
    from: { x: Math.round(a.x + ux * (along - half)), y: Math.round(a.y + uy * (along - half)) },
    to: { x: Math.round(a.x + ux * (along + half)), y: Math.round(a.y + uy * (along + half)) },
  };
}

/** The index of the door of `space` nearest `p`, if one is within `tolerance`. */
export function doorNear(space: LayoutSpace, p: Point, tolerance: number): number | null {
  let index: number | null = null;
  let nearest = tolerance;
  space.doors.forEach((door, i) => {
    const distance = distanceToSegment(p, door.from, door.to);
    if (distance <= nearest) {
      nearest = distance;
      index = i;
    }
  });
  return index;
}

// ── Finding and placing ───────────────────────────────────────────────────────────────────

/** The placed space under a point: the smallest, where one is drawn inside another. */
export function spaceAt(draft: FloorDraft, p: Point): DraftSpace | null {
  const hits = draft.spaces.filter((s) => isPlaced(s) && (inside(s.shape, p) || onOutline(s.shape, p, 0.5)));
  hits.sort((a, b) => area(a.shape as Point[]) - area(b.shape as Point[]));
  return hits[0] ?? null;
}

export type PlacementRefusal = { reason: 'bounds' } | { reason: 'shape' } | { reason: 'overlap'; other: DraftSpace };

/** Why `shape` cannot take the space `key` - or null when it can. */
export function refusePlacement(draft: FloorDraft, key: string | null, shape: Point[]): PlacementRefusal | null {
  if (!within(shape, draft.width, draft.height)) return { reason: 'bounds' };
  if (shape.length < 3 || area(shape) === 0 || !simple(shape)) return { reason: 'shape' };
  const other = draft.spaces.find((s) => s.key !== key && isPlaced(s) && overlaps(s.shape, shape));
  return other ? { reason: 'overlap', other } : null;
}

export function updateSpace(draft: FloorDraft, key: string, patch: Partial<LayoutSpace>): FloorDraft {
  return { ...draft, spaces: draft.spaces.map((s) => (s.key === key ? { ...s, ...patch } : s)) };
}

/** Gives the space a new outline; the doors that are still on it stay. */
export function place(draft: FloorDraft, key: string, shape: Point[]): FloorDraft {
  const space = draft.spaces.find((s) => s.key === key);
  const doors = (space?.doors ?? []).filter((d) => doorOnOutline(shape, d));
  return updateSpace(draft, key, { shape: copy(shape), doors });
}

/** Moves the space, doors and all. */
export function move(draft: FloorDraft, key: string, dx: number, dy: number): FloorDraft {
  const space = draft.spaces.find((s) => s.key === key);
  if (!space || !isPlaced(space)) return draft;
  return updateSpace(draft, key, {
    shape: translate(space.shape, dx, dy),
    doors: space.doors.map((d) => ({ from: { x: d.from.x + dx, y: d.from.y + dy }, to: { x: d.to.x + dx, y: d.to.y + dy } })),
  });
}

function crossing(a: Point, b: Point, axis: 'x' | 'y', at: number): Point {
  const t = (at - a[axis]) / (b[axis] - a[axis]);
  const p = { x: Math.round(a.x + t * (b.x - a.x)), y: Math.round(a.y + t * (b.y - a.y)) };
  p[axis] = at;
  return p;
}

/** The part of an outline on one side of the line `axis` = `at`, corners in a line dropped. */
function clipped(points: Point[], axis: 'x' | 'y', at: number, below: boolean): Point[] {
  const keeps = (p: Point) => (below ? p[axis] <= at : p[axis] >= at);
  const out: Point[] = [];
  points.forEach((b, i) => {
    const a = points[(i + points.length - 1) % points.length];
    if (keeps(b)) {
      if (!keeps(a)) out.push(crossing(a, b, axis, at));
      out.push(b);
    } else if (keeps(a)) {
      out.push(crossing(a, b, axis, at));
    }
  });
  return out.filter((p, i) => {
    const q = out[(i + out.length - 1) % out.length];
    const r = out[(i + 1) % out.length];
    return !(p.x === q.x && p.y === q.y) && !(q.x === p.x && p.x === r.x) && !(q.y === p.y && p.y === r.y);
  });
}

/**
 * Cuts a room in two where the plan drew one but the floor has two: across its longer side, at
 * `at`. The room keeps the larger part and its doors there; the other part becomes a box to name,
 * with the doors on its side. Null when the cut would leave a part with nothing in it.
 */
export function split(
  draft: FloorDraft,
  key: string,
  at: Point,
  floorCode: string,
  taken?: ReadonlySet<string>,
): { draft: FloorDraft; created: DraftSpace } | null {
  const space = draft.spaces.find((s) => s.key === key);
  if (!space || !isPlaced(space)) return null;
  const box = boundsOf(space.shape);
  const axis = box.width >= box.height ? 'x' : 'y';
  const cut = Math.round(at[axis]);
  const low = clipped(space.shape, axis, cut, true);
  const high = clipped(space.shape, axis, cut, false);
  if (low.length < 3 || high.length < 3 || area(low) === 0 || area(high) === 0) return null;
  const [kept, other] = area(low) >= area(high) ? [low, high] : [high, low];
  const created = {
    ...newBox(draft, floorCode, other, taken),
    doors: space.doors.filter((d) => doorOnOutline(other, d) && !doorOnOutline(kept, d)),
  };
  const next = updateSpace(draft, key, { shape: kept, doors: space.doors.filter((d) => doorOnOutline(kept, d)) });
  return { draft: addSpaces(next, [created]), created };
}

export function withVertex(shape: Point[], index: number, p: Point): Point[] {
  return shape.map((q, i) => (i === index ? { x: p.x, y: p.y } : q));
}

/**
 * The wall from corner `edge` to the next pushed across itself, parallel, until it passes through
 * `to`: its two corners go together, so a rectangle stays a rectangle. In whole units.
 */
export function withWallMoved(shape: Point[], edge: number, to: Point): Point[] {
  const a = shape[edge];
  const b = shape[(edge + 1) % shape.length];
  const length = Math.hypot(b.x - a.x, b.y - a.y);
  if (length === 0) return copy(shape);
  const nx = -(b.y - a.y) / length;
  const ny = (b.x - a.x) / length;
  const by = (to.x - a.x) * nx + (to.y - a.y) * ny;
  return shape.map((p, i) =>
    i === edge || i === (edge + 1) % shape.length ? { x: Math.round(p.x + by * nx), y: Math.round(p.y + by * ny) } : { x: p.x, y: p.y },
  );
}

/** A new corner on the edge that starts at `edge`. */
export function withInsertedVertex(shape: Point[], edge: number, p: Point): Point[] {
  return [...shape.slice(0, edge + 1), { x: p.x, y: p.y }, ...shape.slice(edge + 1)];
}

export function withoutVertex(shape: Point[], index: number): Point[] {
  return shape.length <= 3 ? shape : shape.filter((_, i) => i !== index);
}

export function unplace(draft: FloorDraft, key: string): FloorDraft {
  return updateSpace(draft, key, { shape: null, doors: [] });
}

export function addDoor(draft: FloorDraft, key: string, door: Door): FloorDraft {
  const space = draft.spaces.find((s) => s.key === key);
  return space ? updateSpace(draft, key, { doors: [...space.doors, door] }) : draft;
}

export function removeDoor(draft: FloorDraft, key: string, index: number): FloorDraft {
  const space = draft.spaces.find((s) => s.key === key);
  return space ? updateSpace(draft, key, { doors: space.doors.filter((_, i) => i !== index) }) : draft;
}

export function removeSpace(draft: FloorDraft, key: string): FloorDraft {
  return { ...draft, spaces: draft.spaces.filter((s) => s.key !== key) };
}

export function addSpaces(draft: FloorDraft, spaces: DraftSpace[]): FloorDraft {
  return { ...draft, spaces: [...draft.spaces, ...spaces] };
}

/** A box drawn from the evacuation plan: a shape with no name yet, waiting to be matched. */
export function isUnidentified(space: LayoutSpace): boolean {
  return space.typeCode === UNIDENTIFIED_TYPE && !space.doorCode && space.name.trim() === UNIDENTIFIED_NAME;
}

/**
 * Gives the outline and doors of the box `boxKey` to the inventoried space `spaceKey`: the plan
 * drew the shape, the plaque named the room, and this is where the two meet. A box nobody had
 * described yet disappears into the space; one that had been described goes back to the tray
 * instead, because what somebody typed into it is not ours to throw away.
 */
export function assignBox(draft: FloorDraft, boxKey: string, spaceKey: string): FloorDraft {
  const box = draft.spaces.find((s) => s.key === boxKey);
  if (!box || !isPlaced(box) || boxKey === spaceKey) return draft;
  const shape = copy(box.shape);
  const doors = copyDoors(box.doors);
  const cleared = isUnidentified(box) ? removeSpace(draft, boxKey) : unplace(draft, boxKey);
  return updateSpace(cleared, spaceKey, { shape, doors });
}

/** The first `<floor>-NN` code no space on this floor uses and `taken` does not list. */
export function nextCode(draft: FloorDraft, floorCode: string, taken: ReadonlySet<string> = new Set()): string {
  const used = new Set([...draft.spaces.map((s) => s.code.toUpperCase()), ...[...taken].map((t) => t.toUpperCase())]);
  for (let n = 1; ; n++) {
    const code = `${floorCode}-${String(n).padStart(2, '0')}`;
    if (!used.has(code.toUpperCase())) return code;
  }
}

export function newBox(draft: FloorDraft, floorCode: string, shape: Point[] | null, taken?: ReadonlySet<string>): DraftSpace {
  return {
    key: newKey(),
    code: nextCode(draft, floorCode, taken),
    doorCode: null,
    wing: null,
    name: UNIDENTIFIED_NAME,
    typeCode: UNIDENTIFIED_TYPE,
    aliases: [],
    shape: shape ? copy(shape) : null,
    doors: [],
    accessVia: null,
    accessibility: null,
    note: null,
    capacity: null,
  };
}

export interface RangeRequest {
  from: number;
  to: number;
  /** Appended to every number: "-N" for 401-N. Empty for none. */
  suffix: string;
  wing: string | null;
  /** "{n}" is replaced by the bare number, so "Aula {n}" gives "Aula 401" for door 401-N. */
  namePattern: string;
  typeCode: string;
}

/**
 * The rooms an information plaque lists as a range - "401 a 410" - as inventoried spaces, not
 * drawn anywhere yet. A number already on this floor is skipped rather than duplicated.
 */
export function rangeSpaces(draft: FloorDraft, request: RangeRequest): DraftSpace[] {
  const [low, high] = request.from <= request.to ? [request.from, request.to] : [request.to, request.from];
  const doors = new Set(draft.spaces.map((s) => (s.doorCode ?? '').toUpperCase()));
  const codes = new Set(draft.spaces.map((s) => s.code.toUpperCase()));
  const created: DraftSpace[] = [];
  for (let n = low; n <= high && created.length < MAX_SPACES; n++) {
    const door = `${n}${request.suffix.trim()}`;
    if (doors.has(door.toUpperCase()) || codes.has(door.toUpperCase())) continue;
    created.push({
      key: newKey(),
      code: door,
      doorCode: door,
      wing: request.wing,
      name: (request.namePattern.trim() || '{n}').replaceAll('{n}', String(n)),
      typeCode: request.typeCode,
      aliases: [],
      shape: null,
      doors: [],
      accessVia: null,
      accessibility: null,
      note: null,
      capacity: null,
    });
  }
  return created;
}

/**
 * Adds a point to the corridor's walk, or takes out the one within `tolerance` of it. Corridors
 * store their corners in walking order, so a new point goes at the end.
 */
export function toggleCorridorPoint(draft: FloorDraft, index: number, point: Point, tolerance: number): FloorDraft {
  return {
    ...draft,
    corridors: draft.corridors.map((c, i) => {
      if (i !== index) return c;
      const at = c.path.findIndex((p) => Math.hypot(p.x - point.x, p.y - point.y) <= tolerance);
      return { ...c, path: at >= 0 ? c.path.filter((_, j) => j !== at) : [...c.path, { x: point.x, y: point.y }] };
    }),
  };
}

export interface Problem {
  /** The space it is about, to select it from the list. */
  key?: string;
  corridor?: number;
  text: string;
}

export interface ProblemContext {
  categories: ReadonlyMap<string, SpaceCategory>;
  wings: ReadonlySet<string>;
  /** Circulation spaces on the building's other floors: what accessVia may also point at. */
  circulationElsewhere: ReadonlySet<string>;
}

export function label(space: LayoutSpace): string {
  return space.doorCode?.trim() || space.name.trim() || space.code;
}

/**
 * What the server would refuse, found before the round trip so it can be fixed while standing
 * on the floor. The server still checks all of it; this only saves a failed save.
 */
export function problems(draft: FloorDraft, context: ProblemContext): Problem[] {
  const found: Problem[] = [];
  const codeCount = countBy(draft.spaces, (s) => s.code.trim().toUpperCase());
  const doorCount = countBy(draft.spaces.filter((s) => s.doorCode?.trim()), (s) => (s.doorCode as string).trim().toUpperCase());
  const byCode = new Map(draft.spaces.map((s) => [s.code.trim(), s]));

  if (draft.spaces.length > MAX_SPACES) {
    found.push({ text: t('A floor holds at most {MAX_SPACES} spaces; this one has {spaces}.', { MAX_SPACES, spaces: draft.spaces.length }) });
  }
  if (draft.outline.length && !within(draft.outline, draft.width, draft.height)) {
    found.push({ text: t('The building’s outline runs outside the {width} x {height} drawing.', { width: draft.width, height: draft.height }) });
  }

  const drawn: DraftSpace[] = [];
  for (const space of draft.spaces) {
    const name = label(space);
    const code = space.code.trim();
    if (!code || code.length > 20 || !CODE.test(code)) {
      found.push({ key: space.key, text: t('{name}: the internal code must be letters, digits and dashes, up to 20.', { name }) });
    } else if ((codeCount.get(code.toUpperCase()) ?? 0) > 1) {
      found.push({ key: space.key, text: t('{name}: the internal code {code} is used twice on this floor.', { name, code }) });
    }
    if (space.doorCode?.trim() && (doorCount.get(space.doorCode.trim().toUpperCase()) ?? 0) > 1) {
      found.push({ key: space.key, text: t('{name}: door {doorCode} appears twice on this floor.', { name, doorCode: space.doorCode.trim() }) });
    }
    if (!space.name.trim()) {
      found.push({ key: space.key, text: t('{code}: needs a name.', { code: space.code }) });
    }
    if (!context.categories.has(space.typeCode)) {
      found.push({ key: space.key, text: t('{name}: its type {typeCode} is not in the catalogue.', { name, typeCode: space.typeCode }) });
    }
    if (space.wing && !context.wings.has(space.wing)) {
      found.push({ key: space.key, text: t('{name}: wing {wing} is not one of this building’s.', { name, wing: space.wing }) });
    }
    if (isPlaced(space)) {
      if (!within(space.shape, draft.width, draft.height)) {
        found.push({ key: space.key, text: t('{name}: reaches outside the {width} x {height} drawing.', { name, width: draft.width, height: draft.height }) });
      } else if (area(space.shape) === 0) {
        found.push({ key: space.key, text: t('{name}: its outline encloses no area.', { name }) });
      } else if (!simple(space.shape)) {
        found.push({ key: space.key, text: t('{name}: its outline crosses itself.', { name }) });
      } else {
        const shape = space.shape;
        const other = drawn.find((d) => overlaps(d.shape as Point[], shape));
        if (other) found.push({ key: space.key, text: t('{name}: overlaps {value}.', { name, value: label(other) }) });
        drawn.push(space);
      }
      if (space.doors.some((d) => !doorOnOutline(space.shape as Point[], d))) {
        found.push({ key: space.key, text: t('{name}: a door is no longer on its outline.', { name }) });
      }
    } else if (space.doors.length) {
      found.push({ key: space.key, text: t('{name}: has doors but is not drawn.', { name }) });
    }
    const via = space.accessVia?.trim();
    if (via) {
      const target = byCode.get(via);
      if (via === code) {
        found.push({ key: space.key, text: t('{name}: cannot be reached via itself.', { name }) });
      } else if (target && context.categories.get(target.typeCode) !== 'CIRCULATION') {
        found.push({ key: space.key, text: t('{name}: is reached via {value}, which is not a lift, stairs or entrance.', { name, value: label(target) }) });
      } else if (!target && !context.circulationElsewhere.has(via)) {
        found.push({ key: space.key, text: t('{name}: is reached via {via}, which no longer exists in this building.', { name, via }) });
      }
    }
  }

  const corridorCount = countBy(draft.corridors, (c) => c.code.trim().toUpperCase());
  draft.corridors.forEach((corridor, index) => {
    const name = corridor.name.trim() || corridor.code || t('Corridor {value}', { value: index + 1 });
    if (!corridor.code.trim() || !corridor.name.trim()) {
      found.push({ corridor: index, text: t('{name}: a corridor needs a code and a name.', { name }) });
    } else if ((corridorCount.get(corridor.code.trim().toUpperCase()) ?? 0) > 1) {
      found.push({ corridor: index, text: t('{name}: the code {code} is used by two corridors.', { name, code: corridor.code.trim() }) });
    }
    if (!COLOR.test(corridor.color)) {
      found.push({ corridor: index, text: t('{name}: its colour must look like #5B8DEF.', { name }) });
    }
    if (corridor.path.length === 0) {
      found.push({ corridor: index, text: t('{name}: has no points yet - draw it or delete it.', { name }) });
    } else if (!within(corridor.path, draft.width, draft.height)) {
      found.push({ corridor: index, text: t('{name}: runs outside the {width} x {height} drawing.', { name, width: draft.width, height: draft.height }) });
    }
  });
  return found;
}

function countBy<T>(items: T[], keyOf: (item: T) => string): Map<string, number> {
  const counts = new Map<string, number>();
  for (const item of items) {
    const key = keyOf(item);
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return counts;
}

function blankToNull(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

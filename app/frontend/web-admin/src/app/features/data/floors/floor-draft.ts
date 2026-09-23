import type { Accessibility, Corridor, FloorStatus, GridPoint } from '../buildings/building.model';
import type { SpaceCategory } from '../spaces/space.model';
import type { FloorDetail, FloorLayoutRequest, LayoutSpace } from './floor.model';

/**
 * A floor while somebody is editing it: everything a layout save sends, plus a local key per
 * space. The key is what the editor selects by - the internal code is editable, so it cannot be.
 *
 * Every function here is pure and returns a new draft. The page keeps the old ones for undo, and
 * the draft is what gets written to the device between saves.
 */
export interface DraftSpace extends LayoutSpace {
  key: string;
}

export interface FloorDraft {
  gridRows: number;
  gridColumns: number;
  status: FloorStatus;
  accessibility: Accessibility;
  note: string;
  corridors: Corridor[];
  spaces: DraftSpace[];
}

export interface Rect {
  row: number;
  col: number;
  rowSpan: number;
  colSpan: number;
}

/** The type a freshly drawn box gets until somebody says what it is. */
export const UNIDENTIFIED_TYPE = 'OTHER';
export const UNIDENTIFIED_NAME = 'Sin identificar';

const CODE = /^[A-Za-z0-9][A-Za-z0-9-]*$/;
const COLOR = /^#[0-9A-Fa-f]{6}$/;
export const MAX_SPACES = 500;

let counter = 0;

/**
 * A key nobody else on this device has. Not crypto.randomUUID: the portal is also opened from an
 * iPad over plain http on the local network, and outside a secure context that function is absent.
 */
export function newKey(): string {
  counter += 1;
  return `k${Date.now().toString(36)}${counter.toString(36)}`;
}

export function fromDetail(detail: FloorDetail): FloorDraft {
  return {
    gridRows: detail.gridRows,
    gridColumns: detail.gridColumns,
    status: detail.status,
    accessibility: detail.accessibility,
    note: detail.note ?? '',
    corridors: (detail.corridors ?? []).map((c) => ({ ...c, path: c.path.map((p) => ({ ...p })) })),
    spaces: detail.spaces.map((s) => ({
      // Keyed by the code as loaded, so a selection survives a save and a reload.
      key: `c:${s.code}`,
      code: s.code,
      doorCode: s.doorCode ?? null,
      wing: s.wing ?? null,
      name: s.name,
      typeCode: s.typeCode,
      aliases: [...(s.aliases ?? [])],
      gridRow: s.gridRow ?? null,
      gridColumn: s.gridColumn ?? null,
      rowSpan: s.rowSpan,
      colSpan: s.colSpan,
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
    gridRows: draft.gridRows,
    gridColumns: draft.gridColumns,
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
        gridRow: placed ? s.gridRow : null,
        gridColumn: placed ? s.gridColumn : null,
        rowSpan: s.rowSpan,
        colSpan: s.colSpan,
        accessVia: blankToNull(s.accessVia),
        accessibility: s.accessibility ?? null,
        note: blankToNull(s.note),
        capacity: s.capacity ?? null,
      };
    }),
  };
}

/** True when the two drafts would save the same floor. */
export function sameFloor(a: FloorDraft, b: FloorDraft): boolean {
  return JSON.stringify(toRequest(a, 0)) === JSON.stringify(toRequest(b, 0));
}

export function isPlaced(space: LayoutSpace): boolean {
  return space.gridRow != null && space.gridColumn != null;
}

export function rectOf(space: LayoutSpace): Rect | null {
  return isPlaced(space)
    ? { row: space.gridRow as number, col: space.gridColumn as number, rowSpan: space.rowSpan, colSpan: space.colSpan }
    : null;
}

export function fits(rect: Rect, rows: number, columns: number): boolean {
  return rect.row >= 0 && rect.col >= 0 && rect.row + rect.rowSpan <= rows && rect.col + rect.colSpan <= columns;
}

export function intersects(a: Rect, b: Rect): boolean {
  return a.row < b.row + b.rowSpan && b.row < a.row + a.rowSpan && a.col < b.col + b.colSpan && b.col < a.col + a.colSpan;
}

/** The placed space covering a cell, if any. */
export function spaceAt(draft: FloorDraft, row: number, col: number): DraftSpace | null {
  return draft.spaces.find((s) => {
    const r = rectOf(s);
    return r !== null && intersects(r, { row, col, rowSpan: 1, colSpan: 1 });
  }) ?? null;
}

export type PlacementRefusal = { reason: 'bounds' } | { reason: 'overlap'; other: DraftSpace };

/** Why `rect` cannot take the space `key` - or null when it can. */
export function refusePlacement(draft: FloorDraft, key: string | null, rect: Rect): PlacementRefusal | null {
  if (!fits(rect, draft.gridRows, draft.gridColumns)) {
    return { reason: 'bounds' };
  }
  const other = draft.spaces.find((s) => {
    const r = s.key !== key ? rectOf(s) : null;
    return r !== null && intersects(r, rect);
  });
  return other ? { reason: 'overlap', other } : null;
}

export function updateSpace(draft: FloorDraft, key: string, patch: Partial<LayoutSpace>): FloorDraft {
  return { ...draft, spaces: draft.spaces.map((s) => (s.key === key ? { ...s, ...patch } : s)) };
}

export function place(draft: FloorDraft, key: string, rect: Rect): FloorDraft {
  return updateSpace(draft, key, { gridRow: rect.row, gridColumn: rect.col, rowSpan: rect.rowSpan, colSpan: rect.colSpan });
}

export function unplace(draft: FloorDraft, key: string): FloorDraft {
  return updateSpace(draft, key, { gridRow: null, gridColumn: null });
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
 * Gives the place of the box `boxKey` to the inventoried space `spaceKey`: the plan drew the
 * shape, the plaque named the room, and this is where the two meet. A box nobody had described
 * yet disappears into the space; one that had been described goes back to the tray instead,
 * because what somebody typed into it is not ours to throw away.
 */
export function assignBox(draft: FloorDraft, boxKey: string, spaceKey: string): FloorDraft {
  const box = draft.spaces.find((s) => s.key === boxKey);
  const rect = box ? rectOf(box) : null;
  if (!box || !rect || boxKey === spaceKey) return draft;
  const cleared = isUnidentified(box) ? removeSpace(draft, boxKey) : unplace(draft, boxKey);
  return place(cleared, spaceKey, rect);
}

/** The first `<floor>-NN` code no space on this floor uses and `taken` does not list. */
export function nextCode(draft: FloorDraft, floorCode: string, taken: ReadonlySet<string> = new Set()): string {
  const used = new Set([...draft.spaces.map((s) => s.code.toUpperCase()), ...[...taken].map((t) => t.toUpperCase())]);
  for (let n = 1; ; n++) {
    const code = `${floorCode}-${String(n).padStart(2, '0')}`;
    if (!used.has(code.toUpperCase())) return code;
  }
}

export function newBox(draft: FloorDraft, floorCode: string, rect: Rect, taken?: ReadonlySet<string>): DraftSpace {
  return {
    key: newKey(),
    code: nextCode(draft, floorCode, taken),
    doorCode: null,
    wing: null,
    name: UNIDENTIFIED_NAME,
    typeCode: UNIDENTIFIED_TYPE,
    aliases: [],
    gridRow: rect.row,
    gridColumn: rect.col,
    rowSpan: rect.rowSpan,
    colSpan: rect.colSpan,
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
 * placed anywhere yet. A number already on this floor is skipped rather than duplicated.
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
      gridRow: null,
      gridColumn: null,
      rowSpan: 1,
      colSpan: 1,
      accessVia: null,
      accessibility: null,
      note: null,
      capacity: null,
    });
  }
  return created;
}

/**
 * Adds a cell to the corridor's walk, or takes it out if it is already there. Corridors store
 * their corners in walking order, so a new cell goes at the end.
 */
export function toggleCorridorPoint(draft: FloorDraft, index: number, point: GridPoint): FloorDraft {
  return {
    ...draft,
    corridors: draft.corridors.map((c, i) => {
      if (i !== index) return c;
      const at = c.path.findIndex((p) => p.row === point.row && p.col === point.col);
      return { ...c, path: at >= 0 ? c.path.filter((_, j) => j !== at) : [...c.path, { row: point.row, col: point.col }] };
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
    found.push({ text: `A floor holds at most ${MAX_SPACES} spaces; this one has ${draft.spaces.length}.` });
  }

  const owner = new Map<string, DraftSpace>();
  for (const space of draft.spaces) {
    const name = label(space);
    const code = space.code.trim();
    if (!code || code.length > 20 || !CODE.test(code)) {
      found.push({ key: space.key, text: `${name}: the internal code must be letters, digits and dashes, up to 20.` });
    } else if ((codeCount.get(code.toUpperCase()) ?? 0) > 1) {
      found.push({ key: space.key, text: `${name}: the internal code ${code} is used twice on this floor.` });
    }
    if (space.doorCode?.trim() && (doorCount.get(space.doorCode.trim().toUpperCase()) ?? 0) > 1) {
      found.push({ key: space.key, text: `${name}: door ${space.doorCode.trim()} appears twice on this floor.` });
    }
    if (!space.name.trim()) {
      found.push({ key: space.key, text: `${space.code}: needs a name.` });
    }
    if (!context.categories.has(space.typeCode)) {
      found.push({ key: space.key, text: `${name}: its type ${space.typeCode} is not in the catalogue.` });
    }
    if (space.wing && !context.wings.has(space.wing)) {
      found.push({ key: space.key, text: `${name}: wing ${space.wing} is not one of this building's.` });
    }
    const rect = rectOf(space);
    if (rect) {
      if (!fits(rect, draft.gridRows, draft.gridColumns)) {
        found.push({ key: space.key, text: `${name}: does not fit in a ${draft.gridRows} x ${draft.gridColumns} grid.` });
      } else {
        cells: for (let r = rect.row; r < rect.row + rect.rowSpan; r++) {
          for (let c = rect.col; c < rect.col + rect.colSpan; c++) {
            const other = owner.get(`${r}:${c}`);
            if (other && other.key !== space.key) {
              found.push({ key: space.key, text: `${name}: shares a cell with ${label(other)}.` });
              break cells;
            }
            owner.set(`${r}:${c}`, space);
          }
        }
      }
    }
    const via = space.accessVia?.trim();
    if (via) {
      const target = byCode.get(via);
      if (via === code) {
        found.push({ key: space.key, text: `${name}: cannot be reached via itself.` });
      } else if (target && context.categories.get(target.typeCode) !== 'CIRCULATION') {
        found.push({ key: space.key, text: `${name}: is reached via ${label(target)}, which is not a lift, stairs or entrance.` });
      } else if (!target && !context.circulationElsewhere.has(via)) {
        found.push({ key: space.key, text: `${name}: is reached via ${via}, which no longer exists in this building.` });
      }
    }
  }

  const corridorCount = countBy(draft.corridors, (c) => c.code.trim().toUpperCase());
  draft.corridors.forEach((corridor, index) => {
    const name = corridor.name.trim() || corridor.code || `Corridor ${index + 1}`;
    if (!corridor.code.trim() || !corridor.name.trim()) {
      found.push({ corridor: index, text: `${name}: a corridor needs a code and a name.` });
    } else if ((corridorCount.get(corridor.code.trim().toUpperCase()) ?? 0) > 1) {
      found.push({ corridor: index, text: `${name}: the code ${corridor.code.trim()} is used by two corridors.` });
    }
    if (!COLOR.test(corridor.color)) {
      found.push({ corridor: index, text: `${name}: its colour must look like #5B8DEF.` });
    }
    if (corridor.path.length === 0) {
      found.push({ corridor: index, text: `${name}: has no cells yet - draw it or delete it.` });
    } else if (corridor.path.some((p) => p.row >= draft.gridRows || p.col >= draft.gridColumns)) {
      found.push({ corridor: index, text: `${name}: runs outside a ${draft.gridRows} x ${draft.gridColumns} grid.` });
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

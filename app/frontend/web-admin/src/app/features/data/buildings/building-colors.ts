import type { Building } from './building.model';
import { areaPath, toMetres } from '../ground/ground';

/** The colour each wing of a building is drawn in, on every map: north, central, south first. */
const WING_COLORS: Record<string, string> = { N: '#e0564f', C: '#3b82f6', S: '#8b5cf6' };
const MORE_WING_COLORS = ['#0d9488', '#d97706', '#db2777', '#65a30d'];
/** A building whose parts carry no wing. */
export const BUILDING_COLOR = '#c2185b';

/** Each wing's colour, by its code: the same on the campus map, the block editor and the floor list. */
export function wingColors(building: Building): Map<string, string> {
  const colors = new Map<string, string>();
  let next = 0;
  for (const wing of building.wings) {
    colors.set(wing.code, WING_COLORS[wing.code] ?? MORE_WING_COLORS[next++ % MORE_WING_COLORS.length]);
  }
  return colors;
}

/** A building seen from above, small: its parts in their wings' colours, darker the higher they rise. */
export interface Thumbnail {
  viewBox: string;
  parts: { d: string; fill: string; opacity: number }[];
}

/** The building from above, north up, in metres; null for one with no outline yet. */
export function thumbnail(building: Building): Thumbnail | null {
  const parts = (building.footprint ?? []).filter((p) => p.floors > 0 && (p.lowestFloor ?? 1) <= 1 && p.ring.length > 2);
  if (!parts.length) return null;
  const [lon, lat] = parts[0].ring[0];
  const origin = { lon, lat };
  const colors = wingColors(building);
  const drawn = parts.map((p) => {
    const points = p.ring.map((c) => {
      const { east, north } = toMetres(origin, c);
      return { x: east, y: -north };
    });
    return { points, fill: p.wing ? (colors.get(p.wing) ?? BUILDING_COLOR) : BUILDING_COLOR, opacity: 0.25 + Math.min(p.floors, 8) * 0.08 };
  });
  const all = drawn.flatMap((p) => p.points);
  const xs = all.map((p) => p.x);
  const ys = all.map((p) => p.y);
  const pad = Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys)) * 0.06;
  return {
    viewBox: `${Math.min(...xs) - pad} ${Math.min(...ys) - pad} ${Math.max(...xs) - Math.min(...xs) + 2 * pad} ${Math.max(...ys) - Math.min(...ys) + 2 * pad}`,
    parts: drawn.map((p) => ({ d: areaPath(p.points), fill: p.fill, opacity: p.opacity })),
  };
}

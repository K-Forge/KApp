import type { Placement } from '../buildings/building.model';
import type { Coordinate } from './ground.model';
import { drawingToGround, groundToDrawing, margins, nearestQuarter, outlineOf, placementForFloor, reaches, streetLabel, surroundings, toMetres } from './ground';

// The Edificio Central as its seed lays it: top toward the Carrera 9 Bis, 34 units a metre.
const EC: Placement = { origin: { lat: 4.6485371, lon: -74.0611566 }, bearing: 127, metresPerUnit: 0.02927 };

describe('ground', () => {
  it('puts the origin of the drawing on its placement', () => {
    const [lon, lat] = drawingToGround(EC, { x: 0, y: 0 });
    expect(lon).toBeCloseTo(EC.origin.lon, 9);
    expect(lat).toBeCloseTo(EC.origin.lat, 9);
  });

  // Up the drawing is toward the bearing; to its right, a quarter further round.
  it('goes up the drawing toward the bearing, and right a quarter further round', () => {
    const north: Placement = { ...EC, bearing: 0, metresPerUnit: 1 };
    const up = toMetres(north.origin, drawingToGround(north, { x: 0, y: -10 }));
    expect(up.north).toBeCloseTo(10, 6);
    expect(up.east).toBeCloseTo(0, 6);

    const east: Placement = { ...EC, bearing: 90, metresPerUnit: 1 };
    const right = toMetres(east.origin, drawingToGround(east, { x: 10, y: 0 }));
    expect(right.north).toBeCloseTo(-10, 6);
    expect(right.east).toBeCloseTo(0, 6);
  });

  it('brings a point back to where it was on the drawing', () => {
    const p = { x: 1234.5, y: 678.9 };
    const back = groundToDrawing(EC, drawingToGround(EC, p));
    expect(back.x).toBeCloseTo(p.x, 6);
    expect(back.y).toBeCloseTo(p.y, 6);
  });

  it('knows which quarter a bearing is nearest', () => {
    expect(nearestQuarter(127)).toBe('EAST');
    expect(nearestQuarter(359)).toBe('NORTH');
    expect(nearestQuarter(200)).toBe('SOUTH');
  });

  // A floor turned in the editor keeps every point where it was on the ground: turning is how the
  // drawing hangs, not where the building stands.
  it('turns the placement with a floor turned in the editor', () => {
    const width = 2210;
    const height = 2320;
    const p = { x: 400, y: 900 };
    const ground = drawingToGround(EC, p);
    // One quarter clockwise: (x, y) -> (height - y, x), and the top goes from EAST to NORTH.
    const turned = placementForFloor(EC, 'NORTH', height, width);
    const q = groundToDrawing(turned, ground);
    // To a hundredth of a unit, a third of a millimetre: the plane is redrawn about the new origin.
    expect(q.x).toBeCloseTo(height - p.y, 2);
    expect(q.y).toBeCloseTo(p.x, 2);
    expect(turned.bearing).toBeCloseTo(37, 6);
  });

  it('writes a street name along the longest stretch inside the view, never upside down', () => {
    const at = streetLabel([{ x: 100, y: 10 }, { x: 0, y: 10 }, { x: -500, y: 10 }], { x: 0, y: 0, width: 200, height: 100 }, 20);
    expect(at).toEqual({ x: 50, y: 10, angle: 0 });
    expect(streetLabel([{ x: 0, y: 0 }, { x: 5, y: 0 }], { x: 0, y: 0, width: 200, height: 100 }, 20)).toBeNull();
  });

  // A part of the building from above is drawn solid on the floors it rises to, and dashed on the
  // ones above it: an eight-floor core reaches P8, a two-floor base does not reach P3, and only a
  // part with basements reaches below the street.
  it('knows which floors a part of the building reaches', () => {
    expect(reaches({ floors: 8, basements: 2 }, 8)).toBe(true);
    expect(reaches({ floors: 2, basements: 0 }, 3)).toBe(false);
    expect(reaches({ floors: 2, basements: 0 }, 1.5)).toBe(true);
    expect(reaches({ floors: 5, basements: 0 }, -1)).toBe(false);
    expect(reaches({ floors: 0, basements: 2 }, -2)).toBe(true);
  });

  // What the upper floors carry out over a portico is in their margins, and never in the ground floor's.
  it('leaves a part that starts above the street out of the floors below it', () => {
    const overhang = { floors: 5, lowestFloor: 2, basements: 0 };
    expect(reaches(overhang, 1)).toBe(false);
    expect(reaches(overhang, 0)).toBe(false);
    expect(reaches(overhang, 2)).toBe(true);
    expect(reaches(overhang, 5)).toBe(true);
    expect(reaches({ floors: 5, lowestFloor: null, basements: 0 }, 1)).toBe(true);
  });

  // Two parts that share a wall are one outline; the wall they share is not drawn.
  it('outlines neighbouring parts as one', () => {
    const [outline, ...rest] = outlineOf([square(0, 0, 2, 2), square(2, 0, 2, 2)]);
    expect(rest).toEqual([]);
    expect(corners(outline)).toEqual(['0,0', '0,2', '4,0', '4,2']);
  });

  // A part beside two others shares its wall with each in pieces: a corner of theirs lies on it.
  it('outlines a part beside two others', () => {
    const [outline, ...rest] = outlineOf([square(0, 0, 2, 4), square(2, 0, 2, 2), square(2, 2, 2, 2)]);
    expect(rest).toEqual([]);
    expect(corners(outline)).toEqual(['0,0', '0,4', '4,0', '4,4']);
  });

  // Two parts that overlap walk a stretch of wall the same way. Counted once, the walk ran out of
  // wall there and closed the outline straight across the floor: the Edificio Central's south
  // connection showed as a triangle in the floor editor.
  it('walks a wall two overlapping parts share as many times as they do', () => {
    const [outline, ...rest] = outlineOf([square(2, 0, 2, 3), square(2, 1, 1, 1)]);
    expect(rest).toEqual([]);
    // Back at its start along the wall it left by: the corner before the first is the last.
    expect(outline[0]).toEqual([2, 0]);
    expect(outline[outline.length - 1]).toEqual([2, 1]);
  });

  it('keeps parts that do not touch apart', () => {
    expect(outlineOf([square(0, 0, 1, 1), square(5, 5, 1, 1)]).length).toBe(2);
  });

  // The margin groups by wing: the Edificio Central's north wing stays apart from its centre.
  it('draws a margin for each wing, from the parts that reach the floor', () => {
    const base = { lat: EC.origin.lat, lon: EC.origin.lon };
    const at = (x: number, y: number): Coordinate => [base.lon + x * 1e-5, base.lat + y * 1e-5];
    const part = (x: number, wing: string, floors: number) => ({
      lot: '1', floors, basements: 0, wing,
      ring: [at(x, 0), at(x + 1, 0), at(x + 1, 1), at(x, 1), at(x, 0)],
    });
    const footprint = [part(0, 'N', 5), part(1, 'C', 8), part(2, 'C', 8)];
    expect(margins(footprint, EC, 3).map((m) => m.wing)).toEqual(['N', 'C']);
    expect(margins(footprint, EC, 6).map((m) => m.wing)).toEqual(['C']);
  });

  // A part is drawn on the floors it rises to and, dotted, on the one above its top; higher up,
  // and for a part the cadastre does not record, not at all.
  it("shows the cadastre's parts on their floors and the one above", () => {
    const ground = { campus: 'Sede Principal', source: 'IDECA', retrieved: '2026-09-27', blocks: [], sidewalks: [], roadways: [], medians: [], streets: [] };
    const view = { x: 0, y: 0, width: 100, height: 100 };
    const ring = square(0, 0, 1e-4, 1e-4).map(([x, y]) => [EC.origin.lon + x, EC.origin.lat + y] as Coordinate);
    const parts = [{ lot: '1', floors: 5, basements: 0, ring }, { floors: 1, basements: 0, ring }];
    expect(surroundings(ground, EC, view, parts, 5, 4).footprint.map((p) => p.reaches)).toEqual([true]);
    expect(surroundings(ground, EC, view, parts, 6, 5).footprint.map((p) => p.reaches)).toEqual([false]);
    expect(surroundings(ground, EC, view, parts, 7, 6).footprint).toEqual([]);
    expect(surroundings(ground, EC, view, parts, -1, -2).footprint).toEqual([]);
    // Under the ground floor is the basement: a part that only goes down is not drawn on it.
    const cellar = [{ lot: '1', floors: 0, basements: 2, ring }];
    expect(surroundings(ground, EC, view, cellar, 1, -1).footprint).toEqual([]);
  });
});

function square(x: number, y: number, w: number, h: number): Coordinate[] {
  return [[x, y], [x + w, y], [x + w, y + h], [x, y + h], [x, y]];
}

/** A ring's corners, in a fixed order, with the ones in the middle of a straight side dropped. */
function corners(ring: Coordinate[]): string[] {
  const kept = ring.filter((p, i) => {
    const a = ring[(i + ring.length - 1) % ring.length];
    const b = ring[(i + 1) % ring.length];
    return (p[0] - a[0]) * (b[1] - a[1]) !== (p[1] - a[1]) * (b[0] - a[0]);
  });
  return kept.map((p) => `${p[0]},${p[1]}`).sort();
}

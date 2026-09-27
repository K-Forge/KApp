import type { Placement } from '../buildings/building.model';
import { drawingToGround, groundToDrawing, nearestQuarter, placementForFloor, streetLabel, toMetres } from './ground';

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
});

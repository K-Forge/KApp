import {
  alignment,
  areaOf,
  blockOf,
  closeRing,
  distanceAhead,
  edgesOf,
  fromView,
  openRing,
  snapped,
  squared,
  toView,
  wallsOf,
  withEdgeMoved,
  withInsertedVertex,
  withoutVertex,
  type Frame,
  type ViewPoint,
} from './block-geometry';

const EC: Frame = { origin: { lat: 4.6485245, lon: -74.0611566 }, up: 38.13 };

const square: ViewPoint[] = [
  { x: 0, y: 0 },
  { x: 10, y: 0 },
  { x: 10, y: 5 },
  { x: 0, y: 5 },
];

describe('block geometry', () => {
  it('turns the screen so that its top faces the frame\'s direction, and back to the centimetre', () => {
    // 10 m towards 38.13 degrees - the Calle 63 side of the Edificio Central - is straight up.
    const u = 38.13 * (Math.PI / 180);
    const ahead: [number, number] = [
      EC.origin.lon + (10 * Math.sin(u)) / (111320 * Math.cos(EC.origin.lat * (Math.PI / 180))),
      EC.origin.lat + (10 * Math.cos(u)) / 110574,
    ];
    const p = toView(EC, ahead);
    expect(p.x).toBeCloseTo(0, 3);
    expect(p.y).toBeCloseTo(-10, 3);

    const corner: [number, number] = [-74.0613541, 4.6484735];
    const back = fromView(EC, toView(EC, corner));
    expect(back[0]).toBeCloseTo(corner[0], 7);
    expect(back[1]).toBeCloseTo(corner[1], 7);
  });

  it('opens a closed ring and closes it again', () => {
    const ring: [number, number][] = [
      [1, 1],
      [2, 1],
      [2, 2],
      [1, 1],
    ];
    expect(openRing(ring)).toEqual(ring.slice(0, 3));
    expect(closeRing(openRing(ring))).toEqual(ring);
  });

  it('pushes a wall out parallel to itself, so a rectangle stays one', () => {
    // The right-hand wall, dragged 3 m further right (and 1 m down, which does not count).
    const pushed = withEdgeMoved(square, 1, { x: 13, y: 3 });
    expect(pushed).toEqual([
      { x: 0, y: 0 },
      { x: 13, y: 0 },
      { x: 13, y: 5 },
      { x: 0, y: 5 },
    ]);
    expect(areaOf(pushed)).toBeCloseTo(65, 6);
  });

  it('adds a corner on a wall and takes one away, never below three', () => {
    const more = withInsertedVertex(square, 0, { x: 5, y: 0 });
    expect(more).toHaveLength(5);
    expect(more[1]).toEqual({ x: 5, y: 0 });
    expect(withoutVertex(more, 1)).toEqual(square);
    expect(withoutVertex(square.slice(0, 3), 0)).toBeNull();
  });

  it('makes a slanted outline the rectangle round it', () => {
    const slanted: ViewPoint[] = [
      { x: 0, y: 0 },
      { x: 10, y: 2 },
      { x: 9, y: 7 },
      { x: -1, y: 5 },
    ];
    expect(squared(slanted)).toEqual([
      { x: -1, y: 0 },
      { x: 10, y: 0 },
      { x: 10, y: 7 },
      { x: -1, y: 7 },
    ]);
  });

  it('lines a dragged corner up with the nearest line within reach, and rounds it otherwise', () => {
    const targets = [{ x: 10, y: 20 }];
    expect(snapped({ x: 10.3, y: 7.02 }, targets, 0.5)).toEqual({ x: 10, y: 7 });
    expect(snapped({ x: 11.01, y: 19.8 }, targets, 0.5)).toEqual({ x: 11, y: 20 });
    expect(alignment(square, [{ x: 10.4, y: 5.2 }], 0.5)).toEqual([expect.closeTo(0.4, 6), expect.closeTo(0.2, 6)]);
  });

  it('measures each wall, and writes it beside it, along it, never upside down', () => {
    const walls = wallsOf(square, 1);
    expect(walls.map((w) => w.metres)).toEqual([10, 5, 10, 5]);
    // The top wall's length goes above it, the right one's to its right.
    expect(walls[0]).toMatchObject({ at: { x: 5, y: -1 }, angle: 0 });
    expect(walls[1]).toMatchObject({ at: { x: 11, y: 2.5 }, angle: 90 });
    // The bottom wall runs right to left; its length still reads left to right, and inside when asked.
    expect(walls[2].angle).toBeCloseTo(0, 6);
    expect(wallsOf(square, -1)[2].at).toEqual({ x: 5, y: 4 });
  });

  it('finds the sidewalk in front of a wall, unless a neighbour stands between', () => {
    const sidewalk = edgesOf([
      { x: -5, y: -4 },
      { x: 15, y: -4 },
      { x: 15, y: -2 },
      { x: -5, y: -2 },
    ]);
    const top = wallsOf(square, 0)[0];
    expect(distanceAhead(top.middle, top.out, sidewalk, [], 10)).toBeCloseTo(2, 6);
    expect(distanceAhead(top.middle, top.out, sidewalk, [], 1.5)).toBeNull();
    // A neighbour built against the wall: its own wall lies on this one's line.
    const neighbour = edgesOf([
      { x: 0, y: -1 },
      { x: 10, y: -1 },
      { x: 10, y: 0 },
      { x: 0, y: 0 },
    ]);
    expect(distanceAhead(top.middle, top.out, sidewalk, neighbour, 10)).toBeNull();
  });

  it('reads the block from a lot code', () => {
    expect(blockOf('008213024019')).toBe('008213024');
    expect(blockOf(null)).toBeNull();
  });
});

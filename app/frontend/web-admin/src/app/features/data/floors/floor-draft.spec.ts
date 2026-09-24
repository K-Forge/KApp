import type { Point } from '../buildings/building.model';
import type { SpaceCategory } from '../spaces/space.model';
import {
  assignBox,
  doorAt,
  doorOnOutline,
  fromDetail,
  isPlaced,
  labelPoint,
  move,
  newBox,
  nextCode,
  overlaps,
  place,
  problems,
  rangeSpaces,
  rectangle,
  refusePlacement,
  sameFloor,
  simple,
  spaceAt,
  split,
  toRequest,
  toggleCorridorPoint,
  updateSpace,
  type DraftSpace,
  type FloorDraft,
  type ProblemContext,
} from './floor-draft';
import type { FloorDetail } from './floor.model';

function space(code: string, overrides: Partial<DraftSpace> = {}): DraftSpace {
  return {
    key: `k-${code}`,
    code,
    doorCode: code,
    wing: null,
    name: `Aula ${code}`,
    typeCode: 'CLASSROOM',
    aliases: [],
    shape: null,
    doors: [],
    accessVia: null,
    accessibility: null,
    note: null,
    capacity: null,
    ...overrides,
  };
}

const box = (x: number, y: number, width: number, height: number): Point[] => rectangle({ x, y, width, height });

/** An L: 200 wide along the top, 80 wide down the left. */
const L_SHAPE: Point[] = [
  { x: 0, y: 0 },
  { x: 200, y: 0 },
  { x: 200, y: 80 },
  { x: 80, y: 80 },
  { x: 80, y: 200 },
  { x: 0, y: 200 },
];

function floor(spaces: DraftSpace[], overrides: Partial<FloorDraft> = {}): FloorDraft {
  return {
    width: 400,
    height: 240,
    outline: [],
    status: 'DRAFT',
    accessibility: 'STEP_FREE',
    note: '',
    corridors: [],
    spaces,
    ...overrides,
  };
}

const CONTEXT: ProblemContext = {
  categories: new Map<string, SpaceCategory>([
    ['CLASSROOM', 'TEACHING'],
    ['STAIRS', 'CIRCULATION'],
    ['OTHER', 'OTHER'],
  ]),
  wings: new Set(['N', 'S']),
  circulationElsewhere: new Set(['ASC-C']),
};

describe('floor draft', () => {
  it('sends empty text as absent, and an undrawn space with no shape and no doors', () => {
    const detail: FloorDetail = {
      code: 'P4',
      level: 4,
      name: 'Piso 4',
      status: 'DRAFT',
      accessibility: 'STEP_FREE',
      width: 400,
      height: 240,
      version: 3,
      buildingId: 'b',
      buildingCode: 'EC',
      buildingName: 'Edificio Central',
      campus: 'Sede Principal',
      spaces: [
        {
          id: '1', code: '401-N', doorCode: '401-N', name: 'Aula 401', typeCode: 'CLASSROOM', buildingId: 'b',
          buildingCode: 'EC', campus: 'Sede Principal', floorCode: 'P4', floorLevel: 4, aliases: [],
          effectiveAccessibility: 'STEP_FREE',
        },
      ],
    };
    const draft = updateSpace(fromDetail(detail), 'c:401-N', { note: '   ', wing: '' });

    const request = toRequest(draft, detail.version);

    expect(request.version).toBe(3);
    expect(request.width).toBe(400);
    expect(request.note).toBeNull();
    expect(request.spaces[0]).not.toHaveProperty('key');
    expect(request.spaces[0].note).toBeNull();
    expect(request.spaces[0].wing).toBeNull();
    expect(request.spaces[0].shape).toBeNull();
    expect(request.spaces[0].doors).toEqual([]);
  });

  it('treats two drafts that save the same floor as the same, whatever their local keys', () => {
    const a = floor([space('401', { key: 'one' })]);
    const b = floor([space('401', { key: 'two' })]);

    expect(sameFloor(a, b)).toBe(true);
    expect(sameFloor(a, updateSpace(b, 'two', { name: 'Laboratorio' }))).toBe(false);
  });

  describe('geometry', () => {
    it('lets two rooms share a wall, and calls it an overlap once one reaches into the other', () => {
      expect(overlaps(box(0, 0, 100, 80), box(100, 0, 100, 80))).toBe(false);
      expect(overlaps(box(0, 0, 100, 80), box(90, 0, 100, 80))).toBe(true);
      expect(overlaps(box(0, 0, 100, 80), box(0, 0, 100, 80))).toBe(true);
      expect(overlaps(box(0, 0, 200, 200), box(50, 50, 20, 20))).toBe(true);
    });

    it('fits a room into the corner an L leaves, touching it on two walls', () => {
      expect(overlaps(L_SHAPE, box(80, 80, 120, 120))).toBe(false);
      expect(overlaps(L_SHAPE, box(70, 80, 120, 120))).toBe(true);
    });

    it('knows an outline that crosses itself from one that does not', () => {
      expect(simple(L_SHAPE)).toBe(true);
      expect(simple([{ x: 0, y: 0 }, { x: 100, y: 0 }, { x: 0, y: 100 }, { x: 40, y: 100 }])).toBe(false);
    });

    it('labels an L inside itself, not at the middle of its box', () => {
      const at = labelPoint(L_SHAPE);

      expect(spaceAt(floor([space('701', { shape: L_SHAPE })]), at)?.code).toBe('701');
    });

    it('finds the smallest room under a point, and none on bare floor', () => {
      const draft = floor([space('HALL', { shape: box(0, 0, 300, 200) }), space('BOOTH', { shape: box(20, 20, 40, 40) })]);

      expect(spaceAt(draft, { x: 30, y: 30 })?.code).toBe('BOOTH');
      expect(spaceAt(draft, { x: 200, y: 100 })?.code).toBe('HALL');
      expect(spaceAt(draft, { x: 350, y: 220 })).toBeNull();
    });
  });

  it('refuses an outline past the edge, crossing itself or on top of another room - but not over the room itself', () => {
    const draft = floor([space('401', { shape: box(0, 0, 80, 40) })]);

    expect(refusePlacement(draft, null, box(360, 200, 80, 80))).toEqual({ reason: 'bounds' });
    expect(refusePlacement(draft, null, [{ x: 0, y: 100 }, { x: 100, y: 100 }, { x: 0, y: 200 }, { x: 40, y: 200 }])).toEqual({ reason: 'shape' });
    expect(refusePlacement(draft, null, box(40, 0, 40, 40))?.reason).toBe('overlap');
    expect(refusePlacement(draft, 'k-401', box(40, 0, 80, 40))).toBeNull();
  });

  describe('doors', () => {
    it('puts a door on the wall nearest the tap, centred there and kept within that wall', () => {
      const door = doorAt(box(0, 0, 100, 60), { x: 95, y: 58 }, 20);

      expect(door).toEqual({ from: { x: 100, y: 60 }, to: { x: 80, y: 60 } });
      expect(doorOnOutline(box(0, 0, 100, 60), door!)).toBe(true);
    });

    it('carries the doors along when a room moves, and drops the ones a new outline leaves behind', () => {
      const door = { from: { x: 20, y: 40 }, to: { x: 40, y: 40 } };
      const draft = floor([space('401', { shape: box(0, 0, 80, 40), doors: [door] })]);

      const moved = move(draft, 'k-401', 10, 5).spaces[0];
      expect(moved.shape).toEqual(box(10, 5, 80, 40));
      expect(moved.doors).toEqual([{ from: { x: 30, y: 45 }, to: { x: 50, y: 45 } }]);

      expect(place(draft, 'k-401', box(0, 0, 80, 60)).spaces[0].doors).toEqual([]);
      expect(place(draft, 'k-401', box(0, 0, 120, 40)).spaces[0].doors).toEqual([door]);
    });
  });

  it('cuts a room the plan drew as one across its longer side, each part keeping its own doors', () => {
    const left = { from: { x: 20, y: 80 }, to: { x: 40, y: 80 } };
    const right = { from: { x: 150, y: 80 }, to: { x: 170, y: 80 } };
    const draft = floor([space('501', { shape: box(0, 0, 200, 80), doors: [left, right] })]);

    const result = split(draft, 'k-501', { x: 120, y: 30 }, 'P5');

    expect(result?.draft.spaces.find((s) => s.code === '501')).toMatchObject({ shape: box(0, 0, 120, 80), doors: [left] });
    expect(result?.created).toMatchObject({ code: 'P5-01', name: 'Sin identificar', shape: [{ x: 120, y: 0 }, { x: 200, y: 0 }, { x: 200, y: 80 }, { x: 120, y: 80 }], doors: [right] });
    expect(split(draft, 'k-501', { x: 0, y: 30 }, 'P5')).toBeNull();
  });

  it('gives a box its outline and doors to an inventoried space, and the box disappears into it', () => {
    const drawn = { ...newBox(floor([]), 'P4', L_SHAPE), doors: [{ from: { x: 0, y: 150 }, to: { x: 0, y: 180 } }] };
    const draft = floor([drawn, space('403')]);

    const assigned = assignBox(draft, drawn.key, 'k-403');

    expect(assigned.spaces).toHaveLength(1);
    expect(assigned.spaces[0]).toMatchObject({ code: '403', shape: L_SHAPE, doors: drawn.doors });
  });

  it('sends a box somebody already described back to the inventory instead of deleting it', () => {
    const described = space('BANO-P4', { doorCode: null, name: 'Baño', shape: box(0, 0, 40, 40) });
    const draft = floor([described, space('404')]);

    const assigned = assignBox(draft, described.key, 'k-404');

    expect(assigned.spaces).toHaveLength(2);
    expect(isPlaced(assigned.spaces.find((s) => s.code === 'BANO-P4')!)).toBe(false);
    expect(assigned.spaces.find((s) => s.code === '404')?.shape).toEqual(box(0, 0, 40, 40));
  });

  it('turns a plaque range into inventoried spaces, skipping the numbers already on the floor', () => {
    const draft = floor([space('402-N')]);

    const created = rangeSpaces(draft, { from: 403, to: 401, suffix: '-N', wing: 'N', namePattern: 'Aula {n}', typeCode: 'CLASSROOM' });

    expect(created.map((s) => s.doorCode)).toEqual(['401-N', '403-N']);
    expect(created[0]).toMatchObject({ code: '401-N', name: 'Aula 401', wing: 'N', shape: null });
  });

  it('numbers new boxes after the floor, clear of codes taken anywhere in the building', () => {
    const draft = floor([space('P4-01')]);

    expect(nextCode(draft, 'P4', new Set(['P4-02']))).toBe('P4-03');
  });

  it('adds a corridor point at the end of the walk, and a tap near it takes it out', () => {
    const draft = floor([], { corridors: [{ code: 'PAS', name: 'Pasillo', color: '#5B8DEF', path: [{ x: 20, y: 20 }] }] });

    const added = toggleCorridorPoint(draft, 0, { x: 200, y: 20 }, 5);
    expect(added.corridors[0].path).toEqual([{ x: 20, y: 20 }, { x: 200, y: 20 }]);
    expect(toggleCorridorPoint(added, 0, { x: 22, y: 21 }, 5).corridors[0].path).toEqual([{ x: 200, y: 20 }]);
  });

  describe('problems found before saving', () => {
    function texts(draft: FloorDraft): string[] {
      return problems(draft, CONTEXT).map((p) => p.text);
    }

    it('finds nothing wrong with a floor the server would take', () => {
      const stairs = space('ESC-N', { doorCode: null, name: 'Escalera norte', typeCode: 'STAIRS', shape: box(0, 0, 40, 80) });
      const aula = space('501', {
        shape: box(40, 0, 120, 80),
        doors: [{ from: { x: 80, y: 80 }, to: { x: 100, y: 80 } }],
        accessVia: 'ESC-N',
        wing: 'N',
      });
      const lifted = space('502', { accessVia: 'ASC-C' });

      expect(texts(floor([stairs, aula, lifted]))).toEqual([]);
    });

    it('catches a door number used twice and two rooms on top of each other', () => {
      const found = texts(floor([space('401', { shape: box(0, 0, 80, 80) }), space('401B', { doorCode: '401', shape: box(40, 40, 80, 80) })]));

      expect(found.some((t) => t.includes('door 401 appears twice'))).toBe(true);
      expect(found.some((t) => t.includes('overlaps 401'))).toBe(true);
    });

    it('catches a room the drawing no longer holds after shrinking, and a door off its outline', () => {
      const found = texts(
        floor(
          [
            space('610', { shape: box(300, 100, 80, 80) }),
            space('611', { shape: box(0, 0, 80, 80), doors: [{ from: { x: 10, y: 40 }, to: { x: 30, y: 40 } }] }),
          ],
          { height: 150 },
        ),
      );

      expect(found).toEqual(['610: reaches outside the 400 x 150 drawing.', '611: a door is no longer on its outline.']);
    });

    it('catches a way in that is not circulation, or that no longer exists', () => {
      const found = texts(floor([space('401'), space('402', { accessVia: '401' }), space('403', { accessVia: 'ASC-GONE' })]));

      expect(found).toContain('402: is reached via 401, which is not a lift, stairs or entrance.');
      expect(found).toContain('403: is reached via ASC-GONE, which no longer exists in this building.');
    });

    it('catches a corridor nobody drew and a wing the building does not have', () => {
      const found = texts(
        floor([space('401', { wing: 'C' })], { corridors: [{ code: 'PAS', name: 'Pasillo', color: '#5B8DEF', path: [] }] }),
      );

      expect(found).toContain("401: wing C is not one of this building's.");
      expect(found).toContain('Pasillo: has no points yet - draw it or delete it.');
    });
  });
});

import type { SpaceCategory } from '../spaces/space.model';
import {
  assignBox,
  fromDetail,
  isPlaced,
  newBox,
  nextCode,
  problems,
  rangeSpaces,
  refusePlacement,
  sameFloor,
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
    gridRow: null,
    gridColumn: null,
    rowSpan: 1,
    colSpan: 1,
    accessVia: null,
    accessibility: null,
    note: null,
    capacity: null,
    ...overrides,
  };
}

function floor(spaces: DraftSpace[], overrides: Partial<FloorDraft> = {}): FloorDraft {
  return { gridRows: 6, gridColumns: 10, status: 'DRAFT', accessibility: 'STEP_FREE', note: '', corridors: [], spaces, ...overrides };
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
  it('sends empty text as absent and an unplaced space with no position', () => {
    const detail: FloorDetail = {
      code: 'P4',
      level: 4,
      name: 'Piso 4',
      status: 'DRAFT',
      accessibility: 'STEP_FREE',
      gridRows: 6,
      gridColumns: 10,
      version: 3,
      buildingId: 'b',
      buildingCode: 'EC',
      buildingName: 'Edificio Central',
      campus: 'Sede Principal',
      spaces: [
        {
          id: '1', code: '401-N', doorCode: '401-N', name: 'Aula 401', typeCode: 'CLASSROOM', buildingId: 'b',
          buildingCode: 'EC', campus: 'Sede Principal', floorCode: 'P4', floorLevel: 4, aliases: [],
          rowSpan: 1, colSpan: 2, effectiveAccessibility: 'STEP_FREE',
        },
      ],
    };
    const draft = updateSpace(fromDetail(detail), 'c:401-N', { note: '   ', wing: '' });

    const request = toRequest(draft, detail.version);

    expect(request.version).toBe(3);
    expect(request.note).toBeNull();
    expect(request.spaces[0]).not.toHaveProperty('key');
    expect(request.spaces[0].note).toBeNull();
    expect(request.spaces[0].wing).toBeNull();
    expect(request.spaces[0].gridRow).toBeNull();
    expect(request.spaces[0].gridColumn).toBeNull();
  });

  it('treats two drafts that save the same floor as the same, whatever their local keys', () => {
    const a = floor([space('401', { key: 'one' })]);
    const b = floor([space('401', { key: 'two' })]);

    expect(sameFloor(a, b)).toBe(true);
    expect(sameFloor(a, updateSpace(b, 'two', { name: 'Laboratorio' }))).toBe(false);
  });

  it('refuses a place past the edge or on top of another space, but not on the space itself', () => {
    const draft = floor([space('401', { gridRow: 0, gridColumn: 0, colSpan: 2 })]);

    expect(refusePlacement(draft, null, { row: 5, col: 9, rowSpan: 2, colSpan: 1 })).toEqual({ reason: 'bounds' });
    expect(refusePlacement(draft, null, { row: 0, col: 1, rowSpan: 1, colSpan: 1 })?.reason).toBe('overlap');
    expect(refusePlacement(draft, 'k-401', { row: 0, col: 1, rowSpan: 1, colSpan: 2 })).toBeNull();
  });

  it('gives an empty box to an inventoried space, and the box disappears into it', () => {
    const box = newBox(floor([]), 'P4', { row: 1, col: 2, rowSpan: 2, colSpan: 3 });
    const draft = floor([box, space('403')]);

    const assigned = assignBox(draft, box.key, 'k-403');

    expect(assigned.spaces).toHaveLength(1);
    expect(assigned.spaces[0]).toMatchObject({ code: '403', gridRow: 1, gridColumn: 2, rowSpan: 2, colSpan: 3 });
  });

  it('sends a box somebody already described back to the inventory instead of deleting it', () => {
    const described = space('BANO-P4', { doorCode: null, name: 'Baño', gridRow: 0, gridColumn: 0 });
    const draft = floor([described, space('404')]);

    const assigned = assignBox(draft, described.key, 'k-404');

    expect(assigned.spaces).toHaveLength(2);
    expect(isPlaced(assigned.spaces.find((s) => s.code === 'BANO-P4')!)).toBe(false);
    expect(assigned.spaces.find((s) => s.code === '404')).toMatchObject({ gridRow: 0, gridColumn: 0 });
  });

  it('turns a plaque range into inventoried spaces, skipping the numbers already on the floor', () => {
    const draft = floor([space('402-N')]);

    const created = rangeSpaces(draft, { from: 403, to: 401, suffix: '-N', wing: 'N', namePattern: 'Aula {n}', typeCode: 'CLASSROOM' });

    expect(created.map((s) => s.doorCode)).toEqual(['401-N', '403-N']);
    expect(created[0]).toMatchObject({ code: '401-N', name: 'Aula 401', wing: 'N', gridRow: null });
  });

  it('numbers new boxes after the floor, clear of codes taken anywhere in the building', () => {
    const draft = floor([space('P4-01')]);

    expect(nextCode(draft, 'P4', new Set(['P4-02']))).toBe('P4-03');
  });

  it('adds a corridor cell at the end of the walk, and a second tap takes it out', () => {
    const draft = floor([], { corridors: [{ code: 'PAS', name: 'Pasillo', color: '#5B8DEF', path: [{ row: 0, col: 0 }] }] });

    const added = toggleCorridorPoint(draft, 0, { row: 0, col: 1 });
    expect(added.corridors[0].path).toEqual([{ row: 0, col: 0 }, { row: 0, col: 1 }]);
    expect(toggleCorridorPoint(added, 0, { row: 0, col: 0 }).corridors[0].path).toEqual([{ row: 0, col: 1 }]);
  });

  describe('problems found before saving', () => {
    function texts(draft: FloorDraft): string[] {
      return problems(draft, CONTEXT).map((p) => p.text);
    }

    it('finds nothing wrong with a floor the server would take', () => {
      const stairs = space('ESC-N', { doorCode: null, name: 'Escalera norte', typeCode: 'STAIRS', gridRow: 0, gridColumn: 0 });
      const aula = space('501', { gridRow: 0, gridColumn: 1, accessVia: 'ESC-N', wing: 'N' });
      const lifted = space('502', { accessVia: 'ASC-C' });

      expect(texts(floor([stairs, aula, lifted]))).toEqual([]);
    });

    it('catches a door number used twice and two spaces on one cell', () => {
      const found = texts(
        floor([space('401', { gridRow: 1, gridColumn: 1 }), space('401B', { doorCode: '401', gridRow: 1, gridColumn: 1 })]),
      );

      expect(found.some((t) => t.includes('door 401 appears twice'))).toBe(true);
      expect(found.some((t) => t.includes('shares a cell'))).toBe(true);
    });

    it('catches a space the grid no longer fits after shrinking', () => {
      const found = texts(floor([space('610', { gridRow: 5, gridColumn: 9 })], { gridRows: 4 }));

      expect(found).toEqual(['610: does not fit in a 4 x 10 grid.']);
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

      expect(found).toContain('401: wing C is not one of this building\'s.');
      expect(found).toContain('Pasillo: has no cells yet - draw it or delete it.');
    });
  });
});

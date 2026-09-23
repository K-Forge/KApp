import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, type TestRequest } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import type { Building } from '../buildings/building.model';
import type { SpaceType } from '../spaces/space.model';
import { fromDetail, updateSpace } from './floor-draft';
import { loadDraft, storeDraft } from './floor-draft.store';
import { FloorEditorPage } from './floor-editor.page';
import type { FloorDetail, FloorLayoutRequest } from './floor.model';

const BUILDING: Building = {
  id: 'b-b',
  code: 'B',
  name: 'Bloque B',
  campus: 'Sede Principal',
  aliases: [],
  wings: [],
  floors: [{ code: 'P1', level: 1, name: 'Piso 1', gridRows: 4, gridColumns: 8, status: 'DRAFT', accessibility: 'STEP_FREE' }],
};

const TYPES: SpaceType[] = [
  { code: 'CLASSROOM', name: 'Aula', category: 'TEACHING' },
  { code: 'OTHER', name: 'Sin identificar', category: 'OTHER' },
];

function detail(version: number, names: string[] = ['101']): FloorDetail {
  return {
    code: 'P1',
    level: 1,
    name: 'Piso 1',
    status: 'DRAFT',
    accessibility: 'STEP_FREE',
    gridRows: 4,
    gridColumns: 8,
    version,
    buildingId: 'b-b',
    buildingCode: 'B',
    buildingName: 'Bloque B',
    campus: 'Sede Principal',
    corridors: [],
    spaces: names.map((code, i) => ({
      id: `s-${code}`,
      code,
      doorCode: code,
      name: `Aula ${code}`,
      typeCode: 'CLASSROOM',
      buildingId: 'b-b',
      buildingCode: 'B',
      campus: 'Sede Principal',
      floorCode: 'P1',
      floorLevel: 1,
      aliases: [],
      gridRow: 0,
      gridColumn: i,
      rowSpan: 1,
      colSpan: 1,
      effectiveAccessibility: 'STEP_FREE',
    })),
  };
}

/** Node's own localStorage needs a file to work; the device draft needs a real one here. */
function memoryStorage(): Storage {
  const items = new Map<string, string>();
  return {
    get length() {
      return items.size;
    },
    clear: () => items.clear(),
    getItem: (key) => items.get(key) ?? null,
    key: (index) => [...items.keys()][index] ?? null,
    removeItem: (key) => void items.delete(key),
    setItem: (key, value) => void items.set(key, String(value)),
  };
}

describe('FloorEditorPage', () => {
  let fixture: ComponentFixture<FloorEditorPage>;
  let page: FloorEditorPage;
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.stubGlobal('localStorage', memoryStorage());
    await TestBed.configureTestingModule({
      imports: [FloorEditorPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(FloorEditorPage);
    page = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('building', 'B');
    fixture.componentRef.setInput('floor', 'P1');
  });

  afterEach(() => vi.unstubAllGlobals());

  function open(floor: FloorDetail): void {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/api/map/buildings/B')).flush(BUILDING);
    http.expectOne((r) => r.url.endsWith('/api/map/buildings/B/floors/P1')).flush(floor);
    http.expectOne((r) => r.url.endsWith('/api/map/space-types')).flush(TYPES);
    http.expectOne((r) => r.url.endsWith('/api/map/spaces/search')).flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
  }

  function expectSave(): TestRequest {
    return http.expectOne((r) => r.method === 'PUT' && r.url.endsWith('/api/map/buildings/B/floors/P1/layout'));
  }

  it('offers back the changes this device kept, instead of dropping or applying them silently', () => {
    const server = detail(1);
    storeDraft('B', 'P1', 1, updateSpace(fromDetail(server), 'c:101', { name: 'Biblioteca' }));

    open(server);

    expect(page.pendingDraft()).not.toBeNull();
    expect(page.dirty()).toBe(false);

    page.restorePending();
    fixture.detectChanges();

    expect(page.dirty()).toBe(true);
    expect(page.draft()?.spaces[0].name).toBe('Biblioteca');
  });

  it('keeps every change on the device as it is made', () => {
    open(detail(1));

    page.addOne({ doorCode: '102', name: 'Aula 102', typeCode: 'CLASSROOM', wing: null });
    fixture.detectChanges();

    expect(loadDraft('B', 'P1')?.draft.spaces.map((s) => s.code)).toEqual(['101', '102']);
  });

  it('calls a stale version a conflict, and keeps the changes that were not saved', () => {
    open(detail(1));
    page.addOne({ doorCode: '102', name: 'Aula 102', typeCode: 'CLASSROOM', wing: null });
    fixture.detectChanges();

    page.save();
    expectSave().flush(
      {
        timestamp: '2026-09-23T15:00:00Z',
        status: 409,
        error: 'Conflict',
        message: 'Floor P1 of building B was saved by somebody else',
        path: '/api/map/buildings/B/floors/P1/layout',
        details: [{ field: 'version', issue: 'sent 1, the floor is at 2' }],
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(page.conflict()).toBe(true);
    expect(page.saveError()).toBeNull();
    expect(page.dirty()).toBe(true);
    expect(loadDraft('B', 'P1')).not.toBeNull();
  });

  it('sends the version it loaded, and forgets the device copy once the floor is saved', () => {
    open(detail(4));
    page.addOne({ doorCode: '102', name: 'Aula 102', typeCode: 'CLASSROOM', wing: null });
    fixture.detectChanges();

    page.save();
    const request = expectSave();
    const body = request.request.body as FloorLayoutRequest;
    expect(body.version).toBe(4);
    expect(body.spaces.find((s) => s.code === '102')).toMatchObject({ doorCode: '102', gridRow: null, gridColumn: null });

    const saved = detail(5, ['101', '102']);
    saved.spaces[1] = { ...saved.spaces[1], gridRow: null, gridColumn: null };
    request.flush(saved);
    fixture.detectChanges();

    expect(page.dirty()).toBe(false);
    expect(page.detail()?.version).toBe(5);
    expect(loadDraft('B', 'P1')).toBeNull();
  });

  it('names the space a refusal is about, not its position in the request', () => {
    open(detail(1));
    page.addOne({ doorCode: '102', name: 'Aula 102', typeCode: 'CLASSROOM', wing: null });
    fixture.detectChanges();

    page.save();
    expectSave().flush(
      {
        timestamp: '2026-09-23T15:00:00Z',
        status: 400,
        error: 'Bad Request',
        message: 'The floor was not saved: 1 problem(s). Nothing was changed.',
        path: '/api/map/buildings/B/floors/P1/layout',
        details: [{ field: 'spaces[1].doorCode', issue: "door '102' is already on floor P2 of this building" }],
      },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(page.saveError()?.details?.[0].field).toBe('102 · doorCode');
  });
});

import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import type { Building, BuildingRequest } from '../buildings/building.model';
import type { Coordinate, Ground } from '../ground/ground.model';
import { BlockEditorPage } from './block-editor.page';
import type { Structures, StructuresRequest } from './structure.model';

const box = (lon: number, lat: number, size: number): Coordinate[] => [
  [lon, lat],
  [lon + size, lat],
  [lon + size, lat + size],
  [lon, lat + size],
  [lon, lat],
];

const NORTH_WING = box(-74.0613, 4.6485, 0.0001);
const SHED = box(-74.0611, 4.6485, 0.00005);

const EC: Building = {
  id: 'b-ec',
  code: 'EC',
  name: 'Edificio Central',
  campus: 'Sede Principal',
  aliases: [],
  wings: [{ code: 'N', name: 'Ala norte' }],
  floors: [{ code: 'P1', level: 1, name: 'Piso 1', width: 2400, height: 2320 }],
  placement: { origin: { lat: 4.6485245, lon: -74.0611566 }, bearing: 128.13, metresPerUnit: 0.02911 },
  footprint: [
    { lot: '008213024019', floors: 5, basements: 0, wing: 'N', ring: NORTH_WING },
    { lot: null, floors: 1, basements: 0, wing: null, ring: SHED },
  ],
};

/** On another block: drawn around, not reshaped. */
const BI: Building = {
  ...EC,
  id: 'b-bi',
  code: 'BI',
  name: 'Bienestar',
  wings: [],
  footprint: [{ lot: '008213025001', floors: 4, basements: 0, ring: box(-74.0609, 4.6479, 0.0001) }],
};

const GROUND: Ground = {
  campus: 'Sede Principal',
  source: 'IDECA',
  retrieved: '2026-09-28',
  blocks: [],
  sidewalks: [],
  roadways: [],
  medians: [],
  streets: [],
  lots: [{ code: '008213024019', ring: box(-74.0614, 4.6484, 0.0004) }],
};

const STRUCTURES: Structures = {
  campus: 'Sede Principal',
  version: 3,
  structures: [{ name: 'Vecino', floors: 5, basements: 0, lot: '008213024001', ring: box(-74.0614, 4.6484, 0.00008) }],
};

describe('BlockEditorPage', () => {
  let fixture: ComponentFixture<BlockEditorPage>;
  let page: BlockEditorPage;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BlockEditorPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(BlockEditorPage);
    page = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('block', '008213024');
  });

  function open(): void {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/api/map/buildings')).flush([EC, BI]);
    http.expectOne((r) => r.url.endsWith('/ground')).flush(GROUND);
    http.expectOne((r) => r.url.endsWith('/structures')).flush(STRUCTURES);
    fixture.detectChanges();
  }

  it('reshapes the parts of the buildings on the block and what else stands on it, and nothing is unsaved', () => {
    open();

    expect(page.editable().map((b) => b.code)).toEqual(['EC']);
    expect(page.shapes().map((s) => s.key)).toEqual(['part:EC:0', 'part:EC:1', 'structure:0']);
    expect(page.dirty()).toBe(false);
  });

  it('writes a moved part anew and every other one exactly as it was read, and leaves the structures alone', () => {
    open();
    page.select('part:EC:0');
    page.square();
    expect(page.dirty()).toBe(true);

    page.save();
    const request = http.expectOne((r) => r.method === 'PUT' && r.url.endsWith('/api/map/buildings/EC'));
    const body = request.request.body as BuildingRequest;
    expect(body.footprint).toHaveLength(2);
    expect(body.footprint![0].ring).not.toEqual(NORTH_WING);
    expect(body.footprint![0].ring[0]).toEqual(body.footprint![0].ring[4]);
    expect(body.footprint![0]).toMatchObject({ lot: '008213024019', floors: 5, wing: 'N' });
    expect(body.footprint![1].ring).toBe(SHED);
    // The rest of the building goes back as it came, so the floors keep their drawings.
    expect(body.floors).toEqual(EC.floors);
    http.expectNone((r) => r.url.endsWith('/structures') && r.method === 'PUT');
    request.flush({ ...EC, footprint: body.footprint });

    // Read again from the server: nothing left to save.
    http.expectOne((r) => r.url.endsWith('/api/map/buildings')).flush([{ ...EC, footprint: body.footprint }, BI]);
    http.expectOne((r) => r.url.endsWith('/structures')).flush(STRUCTURES);
    expect(page.dirty()).toBe(false);
  });

  it('sends the structures with the version it read, and keeps the changes when somebody saved them since', () => {
    open();
    page.select('structure:0');
    page.square();
    page.save();

    const request = http.expectOne((r) => r.method === 'PUT' && r.url.endsWith('/structures'));
    expect((request.request.body as StructuresRequest).version).toBe(3);
    http.expectNone((r) => r.method === 'PUT' && r.url.includes('/api/map/buildings/'));
    request.flush(
      { timestamp: '2026-09-28T15:00:00Z', status: 409, error: 'Conflict', message: 'Saved by somebody else', path: '/structures' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(page.dirty()).toBe(true);
    expect(page.saveState()).toContain('Somebody saved this block');
  });

  it('turns a quarter without anything looking moved, and undoes an edit back to what was read', () => {
    open();
    page.turn(1);
    expect(page.dirty()).toBe(false);

    page.select('part:EC:1');
    page.remove();
    expect(page.shapes()).toHaveLength(2);
    expect(page.dirty()).toBe(true);
    page.undo();
    expect(page.dirty()).toBe(false);
  });
});

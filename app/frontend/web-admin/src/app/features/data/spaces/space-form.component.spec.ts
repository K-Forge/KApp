import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import type { Building } from '../buildings/building.model';
import { SpaceFormComponent } from './space-form.component';
import type { Space, SpaceRequest } from './space.model';

const BLOQUE_A: Building = {
  id: 'b-a',
  code: 'A',
  name: 'Bloque A',
  campus: 'Sede Principal',
  aliases: [],
  wings: [
    { code: 'C', name: 'Ala central' },
    { code: 'S', name: 'Ala sur', doorSuffix: '-S' },
  ],
  floors: [
    { code: 'P5', level: 5, name: 'Piso 5', width: 400, height: 240, accessibility: 'STEP_FREE' },
    { code: 'P6', level: 6, name: 'Piso 6', width: 400, height: 240 },
  ],
};

const BLOQUE_B: Building = {
  id: 'b-b',
  code: 'B',
  name: 'Bloque B',
  campus: 'Sede Principal',
  aliases: [],
  wings: [],
  floors: [{ code: 'P1', level: 1, name: 'Piso 1', width: 320, height: 160 }],
};

const AULA_503: Space = {
  id: 's-1',
  code: '503-S',
  doorCode: '503-S',
  wing: 'S',
  name: 'Aula 503',
  typeCode: 'CLASSROOM',
  buildingId: 'b-a',
  buildingCode: 'A',
  campus: 'Sede Principal',
  floorCode: 'P5',
  floorLevel: 5,
  aliases: ['S-503'],
  shape: [
    { x: 80, y: 40 },
    { x: 160, y: 40 },
    { x: 160, y: 80 },
    { x: 80, y: 80 },
  ],
  doors: [{ from: { x: 100, y: 80 }, to: { x: 120, y: 80 } }],
  accessVia: 'ASC-C',
  effectiveAccessibility: 'STEP_FREE',
};

describe('SpaceFormComponent', () => {
  let fixture: ComponentFixture<SpaceFormComponent>;
  let form: SpaceFormComponent;
  let http: HttpTestingController;
  let emitted: SpaceRequest[];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SpaceFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(SpaceFormComponent);
    form = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('buildings', [BLOQUE_A, BLOQUE_B]);
    emitted = [];
    form.submitted.subscribe((request) => emitted.push(request));
  });

  function edit(space: Space): void {
    fixture.componentRef.setInput('initial', space);
    fixture.detectChanges();
  }

  function circulationRequest() {
    return http.expectOne((request) => request.url.endsWith('/api/map/spaces/search'));
  }

  it('asks for the circulation of the building, and never offers a space as its own way in', () => {
    edit(AULA_503);

    const request = circulationRequest();
    expect(request.request.params.get('buildingCode')).toBe('A');
    expect(request.request.params.get('category')).toBe('CIRCULATION');
    request.flush({
      content: [
        { ...AULA_503, code: 'ESC-N', name: 'Escalera norte', floorCode: 'P5', floorLevel: 5 },
        { ...AULA_503, code: 'ASC-C', name: 'Ascensor central', floorCode: 'P1', floorLevel: 1 },
        { ...AULA_503, code: '503-S' },
      ],
      page: 0,
      size: 100,
      totalElements: 3,
      totalPages: 1,
    });

    expect(form.circulation().map((s) => s.code)).toEqual(['ASC-C', 'ESC-N']);
  });

  it('keeps the outline and doors drawn on its floor, and lets them go when it moves to another', () => {
    edit(AULA_503);
    circulationRequest().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });

    form.submit();
    form.form.controls.floorCode.setValue('P6');
    form.onFloorChange();
    form.submit();

    expect(emitted).toHaveLength(2);
    expect(emitted[0].shape).toEqual(AULA_503.shape);
    expect(emitted[0].doors).toEqual(AULA_503.doors);
    expect(emitted[1].shape).toBeNull();
    expect(emitted[1].doors).toEqual([]);
  });

  it('sends an empty field as absent, so the server reads it as not set rather than set to empty', () => {
    edit(AULA_503);
    circulationRequest().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });

    form.form.patchValue({ doorCode: '  ', wing: '', accessVia: '', accessibility: '', note: ' ' });
    form.submit();

    expect(emitted).toHaveLength(1);
    const [request] = emitted;
    expect(request.doorCode).toBeNull();
    expect(request.wing).toBeNull();
    expect(request.accessVia).toBeNull();
    expect(request.accessibility).toBeNull();
    expect(request.note).toBeNull();
    expect(request.aliases).toEqual(['S-503']);
  });

  it('forgets the floor and the wing when the building changes, since they belong to the old one', () => {
    edit(AULA_503);
    circulationRequest().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });

    form.form.controls.buildingCode.setValue('B');
    form.onBuildingChange();
    circulationRequest().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });

    expect(form.form.controls.floorCode.value).toBe('');
    expect(form.form.controls.wing.value).toBe('');
    expect(form.form.controls.accessVia.value).toBe('');
    expect(form.floors().map((f) => f.code)).toEqual(['P1']);
    expect(form.wings()).toEqual([]);
  });

  it('lets the internal code follow the door number until somebody types into it', () => {
    fixture.detectChanges();

    form.form.controls.doorCode.setValue('401-N');
    form.mirrorDoorCode();
    expect(form.form.controls.code.value).toBe('401-N');

    form.form.controls.code.setValue('AULA-401');
    form.form.controls.code.markAsDirty();
    form.form.controls.doorCode.setValue('402-N');
    form.mirrorDoorCode();
    expect(form.form.controls.code.value).toBe('AULA-401');
  });
});

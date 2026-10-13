import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import type { Building } from '../buildings/building.model';
import type { Ground } from '../ground/ground.model';
import { SURVEY_PLAN } from './survey-plan';
import type { Survey, SurveyRequest } from './survey.model';
import { SurveyPage } from './survey.page';

const EC: Building = {
  id: 'b-ec',
  code: 'EC',
  name: 'Edificio Central',
  campus: 'Sede Principal',
  aliases: [],
  wings: [],
  floors: [],
  footprint: [
    {
      lot: '008213024019',
      floors: 5,
      basements: 0,
      ring: [
        [-74.0613, 4.6485],
        [-74.0612, 4.6485],
        [-74.0612, 4.6486],
        [-74.0613, 4.6485],
      ],
    },
  ],
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
};

const KEY = 'kapp-admin:survey-pending:sede principal';

/** Node's own localStorage needs a file to work; what is kept on the device needs a real one here. */
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

describe('SurveyPage', () => {
  let fixture: ComponentFixture<SurveyPage>;
  let page: SurveyPage;
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.stubGlobal('localStorage', memoryStorage());
    await TestBed.configureTestingModule({
      imports: [SurveyPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(SurveyPage);
    page = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    fixture.destroy();
    vi.unstubAllGlobals();
  });

  function open(survey: Survey): void {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/api/map/buildings')).flush([EC]);
    http.expectOne((r) => r.url.endsWith('/ground')).flush(GROUND);
    http.expectOne((r) => r.url.endsWith('/structures')).flush({ campus: 'Sede Principal', structures: [], version: 1 });
    http.expectOne((r) => r.method === 'GET' && r.url.endsWith('/survey')).flush(survey);
    fixture.detectChanges();
  }

  const typed = (value: string) => ({ target: { value } }) as unknown as Event;

  it('opens at the first distance not taken yet, with every one of the plan on the sketch', () => {
    open({ campus: 'Sede Principal', version: 2, measures: [{ id: 'bis-01', text: '2,31', metres: 2.31 }] });

    expect(page.current().id).toBe('bis-02');
    expect(page.done()).toBe(1);
    expect(page.view()?.lines).toHaveLength(SURVEY_PLAN.length);
    expect(fixture.nativeElement.textContent).toContain(`1 / ${SURVEY_PLAN.length}`);
  });

  it('saves what is typed with the version it read, and keeps it on the device until the server has it', () => {
    open({ campus: 'Sede Principal', version: 2, measures: [{ id: 'bis-01', text: '2,31', metres: 2.31 }] });

    page.write('bis-02', 'text', typed('3,10 + 1,02'));
    expect(page.reading().metres).toBeCloseTo(4.12, 6);
    expect(JSON.parse(localStorage.getItem(KEY)!)['bis-02'].text).toBe('3,10 + 1,02');

    page.save();
    const request = http.expectOne((r) => r.method === 'PUT' && r.url.endsWith('/survey'));
    const body = request.request.body as SurveyRequest;
    expect(body.version).toBe(2);
    expect(body.measures).toEqual([
      { id: 'bis-01', text: '2,31', metres: 2.31 },
      { id: 'bis-02', text: '3,10 + 1,02', metres: 4.12 },
    ]);
    request.flush({ campus: 'Sede Principal', version: 3, measures: body.measures });

    expect(page.pending()).toEqual({});
    expect(page.saveState()).toBe('saved');
    expect(JSON.parse(localStorage.getItem(KEY)!)).toEqual({});
  });

  it('when another device saved since, reads the survey again and lays its own change over it', async () => {
    open({ campus: 'Sede Principal', version: 2, measures: [] });

    page.write('bis-03', 'text', typed('8,40'));
    page.save();
    http
      .expectOne((r) => r.method === 'PUT')
      .flush({ status: 409, message: 'Saved from somewhere else' }, { status: 409, statusText: 'Conflict' });
    http
      .expectOne((r) => r.method === 'GET' && r.url.endsWith('/survey'))
      .flush({ campus: 'Sede Principal', version: 5, measures: [{ id: 'bis-01', text: '2,30', metres: 2.3 }] });
    await new Promise((resolve) => setTimeout(resolve, 5));

    const again = http.expectOne((r) => r.method === 'PUT');
    expect((again.request.body as SurveyRequest).version).toBe(5);
    expect((again.request.body as SurveyRequest).measures.map((m) => m.id)).toEqual(['bis-01', 'bis-03']);
  });

  it('keeps a change the server never got, and sends it the next time the sheet opens', async () => {
    localStorage.setItem(KEY, JSON.stringify({ 'bis-05': { id: 'bis-05', text: '2,07', metres: 2.07 } }));
    open({ campus: 'Sede Principal', version: 1, measures: [] });
    await new Promise((resolve) => setTimeout(resolve, 5));

    const request = http.expectOne((r) => r.method === 'PUT');
    expect((request.request.body as SurveyRequest).measures).toEqual([{ id: 'bis-05', text: '2,07', metres: 2.07 }]);
  });
});

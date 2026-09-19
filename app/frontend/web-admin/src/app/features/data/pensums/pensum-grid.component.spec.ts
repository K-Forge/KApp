import { TestBed } from '@angular/core/testing';
import { PensumGridComponent } from './pensum-grid.component';
import type { Pensum, PensumCourse } from './pensum.model';

function course(code: string, name: string, level: number, area: string, credits: number, weeklyHours: number,
                extra: Partial<PensumCourse> = {}): PensumCourse {
  return { code, pensumItemCode: code, name, level, credits, weeklyHours, area, isElectiveSlot: false, prerequisites: [], ...extra };
}

const PRINTED_GRID: Pensum = {
  pensumCode: '1015', programCode: '506', programName: 'Ingeniería de Sistemas', faculty: 'Ingenierías',
  reform: 'Plan de estudios 2019-1', status: 'ACTIVE', totalCredits: 11, totalHours: 13, levels: 3,
  areas: [
    { code: 'CB', name: 'Ciencias Básicas', color: '#539392', credits: 6, hours: 8 },
    { code: 'SI', name: 'Sociedad e Interculturalidad', color: '#D51A65', credits: 5, hours: 5 },
  ],
  courses: [
    course('11015', 'Precálculo', 1, 'CB', 3, 4),
    course('12015', 'Cálculo I', 2, 'CB', 3, 4, { prerequisites: ['11015'] }),
    course('71221', 'Cultura I', 2, 'SI', 2, 2),
    { ...course('59075', 'Electiva I', 3, 'SI', 3, 3), code: null, isElectiveSlot: true },
  ],
};

function render(pensum: Pensum) {
  const fixture = TestBed.createComponent(PensumGridComponent);
  fixture.componentRef.setInput('pensum', pensum);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('PensumGridComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PensumGridComponent] }).compileComponents();
  });

  // The point of the grid: an item in the wrong semester or area sits in a different box than
  // on paper. So the box an item lands in is the thing to assert.
  it('puts every item in the cell of its area and level', () => {
    const el = render(PRINTED_GRID);
    const cells = Array.from(el.querySelectorAll('.cell')) as HTMLElement[];
    const names = cells.map((cell) => Array.from(cell.querySelectorAll('.item-name')).map((n) => n.textContent!.trim()));

    // two areas x three levels, row by row
    expect(names).toEqual([
      ['Precálculo'], ['Cálculo I'], [],
      [], ['Cultura I'], ['Electiva I'],
    ]);
  });

  it('labels the levels in roman numerals and totals each one', () => {
    const el = render(PRINTED_GRID);
    const heads = Array.from(el.querySelectorAll('.level-head')).map((h) => h.textContent!.trim());
    const totals = Array.from(el.querySelectorAll('.level-total')).map((t) => t.textContent!.trim());

    expect(heads).toEqual(['I', 'II', 'III']);
    expect(totals).toEqual(['3 cr · 4 h', '5 cr · 6 h', '3 cr · 3 h']);
  });

  // Four items across the published plans print half an hour. The grid is what those are
  // checked against the PDF with, so it has to show the half rather than a rounded figure.
  it('shows half an hour as a half, in the item and in the level total', () => {
    const withPractice: Pensum = {
      ...PRINTED_GRID,
      totalHours: 12.5,
      courses: [...PRINTED_GRID.courses.slice(0, 3), course('P5805', 'Práctica profesional', 3, 'SI', 9, 4.5)],
    };
    const el = render(withPractice);

    expect(el.textContent).toContain('9 cr · 4.5 h');
    expect(Array.from(el.querySelectorAll('.level-total')).map((t) => t.textContent!.trim()))
      .toEqual(['3 cr · 4 h', '5 cr · 6 h', '9 cr · 4.5 h']);
  });

  it('shows the pensum item code of an elective slot and its prerequisites by code', () => {
    const el = render(PRINTED_GRID);
    const slot = el.querySelector('.item.slot') as HTMLElement;

    expect(slot.querySelector('.code')!.textContent!.trim()).toBe('59075');
    expect(el.textContent).toContain('needs 11015');
  });

  // A brochure that prints neither credits nor hours stores zeros. Showing "0 cr · 0 h" would say
  // the courses are worth nothing; the grid says the document does not publish the figures.
  it('leaves out figures the source document does not print, and says so', () => {
    const brochure: Pensum = {
      ...PRINTED_GRID, totalCredits: 33, totalHours: 0,
      courses: PRINTED_GRID.courses.map((c) => ({ ...c, credits: 0, weeklyHours: 0 })),
    };
    const el = render(brochure);

    expect(el.querySelector('.item-meta')).toBeNull();
    expect(el.querySelector('.missing')!.textContent!.trim())
      .toBe('The published plan prints neither credits nor weekly hours per course — only a total of 33 credits.');
    expect(Array.from(el.querySelectorAll('.level-total')).map((t) => t.textContent!.trim()))
      .toEqual(['1 course', '2 courses', '1 course']);
  });

  it('orders the items of a cell the way Spanish does, accents included', () => {
    const accented: Pensum = {
      ...PRINTED_GRID,
      courses: [
        course('90001', 'Violencia y sexualidad', 1, 'CB', 2, 2),
        course('90002', 'Ética y jurídica en la sexualidad', 1, 'CB', 2, 2),
      ],
    };
    const names = Array.from(render(accented).querySelectorAll('.cell')[0].querySelectorAll('.item-name'))
      .map((n) => n.textContent!.trim());

    expect(names).toEqual(['Ética y jurídica en la sexualidad', 'Violencia y sexualidad']);
  });
});

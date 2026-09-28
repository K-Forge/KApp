import { TestBed } from '@angular/core/testing';
import type { SpaceCategory } from '../spaces/space.model';
import type { DraftSpace } from './floor-draft';
import { FloorLegendComponent } from './floor-legend.component';

function space(code: string, overrides: Partial<DraftSpace> = {}): DraftSpace {
  return {
    key: `k-${code}`,
    code,
    doorCode: code,
    wing: null,
    name: `Aula ${code}`,
    typeCode: 'CLASSROOM',
    aliases: [],
    shape: [
      { x: 0, y: 0 },
      { x: 10, y: 0 },
      { x: 10, y: 10 },
    ],
    doors: [],
    accessVia: null,
    accessibility: null,
    note: null,
    capacity: null,
    ...overrides,
  };
}

describe('FloorLegendComponent', () => {
  function render(spaces: DraftSpace[]) {
    const fixture = TestBed.createComponent(FloorLegendComponent);
    const categories = new Map<string, SpaceCategory>([
      ['CLASSROOM', 'TEACHING'],
      ['STAIRS', 'CIRCULATION'],
      ['OTHER', 'OTHER'],
    ]);
    fixture.componentRef.setInput('spaces', spaces);
    fixture.componentRef.setInput('categories', categories);
    fixture.detectChanges();
    return fixture;
  }

  // The key doubles as a count of how far the floor is from named, so it counts what the plan
  // draws: placed rooms only, each under the colour it is drawn in.
  it('counts the rooms the plan draws under each colour', () => {
    const fixture = render([
      space('301'),
      space('302'),
      space('303', { shape: null }),
      space('E1', { typeCode: 'STAIRS' }),
      space('P3-1', { typeCode: 'OTHER', doorCode: null, name: 'Sin identificar' }),
    ]);
    const counts = Object.fromEntries(
      fixture.componentInstance.fills().map((item) => [item.category, item.count]),
    );
    expect(counts['TEACHING']).toBe(2);
    expect(counts['CIRCULATION']).toBe(1);
    expect(counts['OTHER']).toBe(1);
    expect(counts['OFFICE']).toBe(0);
  });

  // An empty colour still says what it would mean; it just steps back.
  it('keeps every colour in the key and dims the ones the floor does not use', () => {
    const fixture = render([space('301')]);
    const chips = [...fixture.nativeElement.querySelectorAll('.chip')] as HTMLElement[];
    expect(chips.length).toBe(7);
    expect(chips.filter((chip) => chip.classList.contains('empty')).length).toBe(6);
  });

  // The cadastral outline has its own switch; with it off, the key must not explain lines the
  // plan no longer draws.
  it('explains the cadastre only while it is drawn', () => {
    const fixture = render([space('301')]);
    fixture.componentRef.setInput('groundSource', 'IDECA');
    fixture.detectChanges();
    const text = () => fixture.nativeElement.textContent as string;
    expect(text()).toContain('Sidewalk');
    expect(text()).not.toContain('per the cadastre');

    fixture.componentRef.setInput('cadastre', true);
    fixture.detectChanges();
    expect(text()).toContain('per the cadastre');
  });
});

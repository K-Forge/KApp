import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { SortHeaderComponent, sortRows, type Sort } from './sort-header.component';

describe('sorting a table by a column', () => {
  const rooms = [
    { name: 'Aula 10', capacity: 40 },
    { name: 'aula 9', capacity: null },
    { name: 'Árbol', capacity: 12 },
    { name: '', capacity: 30 },
  ];

  it('reads text as a person does: numbers in order, accents and case aside, the blanks last', () => {
    expect(sortRows(rooms, (r) => r.name, 1).map((r) => r.name)).toEqual(['Árbol', 'aula 9', 'Aula 10', '']);
    expect(sortRows(rooms, (r) => r.name, -1).map((r) => r.name)).toEqual(['Aula 10', 'aula 9', 'Árbol', '']);
  });

  it('compares numbers as numbers, and a room with no capacity stays at the end either way', () => {
    expect(sortRows(rooms, (r) => r.capacity, 1).map((r) => r.capacity)).toEqual([12, 30, 40, null]);
    expect(sortRows(rooms, (r) => r.capacity, -1).map((r) => r.capacity)).toEqual([40, 30, 12, null]);
  });

  it('settles a tie with the second rule it is given', () => {
    const tied = [
      { floor: 'P2', name: 'B' },
      { floor: 'P1', name: 'Z' },
      { floor: 'P2', name: 'A' },
    ];
    const byName = (a: { name: string }, b: { name: string }) => a.name.localeCompare(b.name);
    expect(sortRows(tied, (r) => r.floor, 1, byName).map((r) => r.name)).toEqual(['Z', 'A', 'B']);
  });

  it('sorts by its column on the first tap, and turns it round on the next', () => {
    const fixture = TestBed.createComponent(SortHeaderComponent);
    fixture.componentRef.setInput('appSort', 'capacity');
    fixture.componentRef.setInput('sort', { key: 'name', dir: 1 } satisfies Sort);
    const header = fixture.componentInstance;

    header.toggle();
    expect(header.sort()).toEqual({ key: 'capacity', dir: 1 });
    expect(header.ariaSort()).toBe('ascending');
    header.toggle();
    expect(header.sort()).toEqual({ key: 'capacity', dir: -1 });
    expect(header.ariaSort()).toBe('descending');
  });
});

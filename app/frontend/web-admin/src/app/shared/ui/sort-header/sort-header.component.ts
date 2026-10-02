import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';

/** Which column a table is sorted by, and which way: 1 from A to Z, -1 back. */
export interface Sort {
  key: string;
  dir: 1 | -1;
}

const TEXT = new Intl.Collator('es', { sensitivity: 'base', numeric: true });

/** Two texts as a person reads them: accents and case aside, "Aula 9" before "Aula 10". */
export function compareText(a: string | null | undefined, b: string | null | undefined): number {
  return TEXT.compare(a ?? '', b ?? '');
}

/**
 * Rows in the order a column asks for. Text is compared the way a person reads it - "Aula 9"
 * before "Aula 10", accents and case ignored - numbers as numbers, and a row with nothing in the
 * column goes last whichever way it runs, so the blanks never bury the rows that have a value.
 */
export function sortRows<T>(
  rows: readonly T[],
  value: (row: T) => string | number | null | undefined,
  dir: 1 | -1,
  tie?: (a: T, b: T) => number,
): T[] {
  const blank = (v: string | number | null | undefined) => v === null || v === undefined || v === '';
  return [...rows].sort((a, b) => {
    const va = value(a);
    const vb = value(b);
    if (blank(va) || blank(vb)) return blank(va) === blank(vb) ? (tie?.(a, b) ?? 0) : blank(va) ? 1 : -1;
    const by = typeof va === 'number' && typeof vb === 'number' ? va - vb : compareText(String(va), String(vb));
    return by !== 0 ? dir * by : (tie?.(a, b) ?? 0);
  });
}

/**
 * A column heading that sorts its table: one tap sorts by it, the next turns it round. The arrow
 * shows which column leads and which way; the others show a faint pair, so it reads as something
 * to tap.
 *
 * <p>`<th appSort="name" [(sort)]="sort">Name</th>`
 */
@Component({
  selector: 'th[appSort]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.aria-sort]': 'ariaSort()' },
  template: `
    <button type="button" class="sort-btn" [class.on]="on()" (click)="toggle()">
      <ng-content />
      <svg viewBox="0 0 12 12" width="11" height="11" aria-hidden="true">
        @if (!on()) {
          <path d="M3.5 4.75 6 2.25l2.5 2.5M3.5 7.25 6 9.75l2.5-2.5" />
        } @else if (sort().dir === 1) {
          <path d="M3 4.5 6 1.5l3 3M6 1.5v9" />
        } @else {
          <path d="M3 7.5 6 10.5l3-3M6 10.5v-9" />
        }
      </svg>
    </button>
  `,
  styles: `
    .sort-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      margin: -0.25rem -0.4rem;
      padding: 0.25rem 0.4rem;
      border: 0;
      border-radius: var(--radius-sm);
      background: none;
      color: inherit;
      font: inherit;
      letter-spacing: inherit;
      text-transform: inherit;
      white-space: nowrap;
      cursor: pointer;
    }
    .sort-btn:hover {
      background: var(--bg-hover);
      color: var(--text);
    }
    .sort-btn.on {
      color: var(--text);
    }
    svg {
      fill: none;
      stroke: currentColor;
      stroke-width: 1.5;
      stroke-linecap: round;
      stroke-linejoin: round;
      opacity: 0.45;
    }
    .on svg {
      opacity: 1;
      color: var(--primary);
    }
  `,
})
export class SortHeaderComponent {
  readonly key = input.required<string>({ alias: 'appSort' });
  readonly sort = model.required<Sort>();

  readonly on = computed(() => this.sort().key === this.key());
  readonly ariaSort = computed(() => (this.on() ? (this.sort().dir === 1 ? 'ascending' : 'descending') : null));

  toggle(): void {
    this.sort.set({ key: this.key(), dir: this.on() && this.sort().dir === 1 ? -1 : 1 });
  }
}

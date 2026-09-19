import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { Pensum, PensumArea, PensumCourse } from './pensum.model';

const ROMAN = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X', 'XI', 'XII'];

interface GridRow {
  area: PensumArea;
  /** One entry per level, in level order; each holds that area's items at that level. */
  cells: PensumCourse[][];
  credits: number;
}

/**
 * A pensum drawn the way the university prints it: knowledge areas down the side, levels across
 * the top, one box per item.
 *
 * <p>The table beside it is the exact data; this is what makes a pensum checkable against its
 * PDF at a glance. A course in the wrong semester or the wrong area is invisible in a sorted
 * table and obvious in the grid, because it sits in a different box than it does on paper.
 *
 * <p>Prerequisites are listed by code under each item rather than drawn as arrows. Arrows
 * across a nine-column grid collide with every box between the two ends, which is exactly why
 * the printed plans are hard to read, and the codes are what an admin needs to check anyway.
 */
@Component({
  selector: 'app-pensum-grid',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!hasCredits() || !hasHours()) {
      <!-- A zero here would read as "this course is worth nothing". It means the document the
           pensum was taken from does not print the figure, which is what the admin needs to know. -->
      <p class="hint missing">{{ missingNote() }}</p>
    }
    <div class="scroll-x">
      <!-- No table roles: the table view beside this is the accessible, exact form of the same
           data, and ARIA rows on display:contents elements are unreliable across browsers. -->
      <section class="grid" [style.--levels]="pensum().levels"
               [attr.aria-label]="pensum().programName + ' ' + pensum().pensumCode + ', by area and level'">
        <div class="row-group">
          <div class="corner">Area</div>
          @for (label of levelLabels(); track $index) {
            <div class="level-head">{{ label }}</div>
          }
        </div>

        @for (row of rows(); track row.area.code) {
          <div class="row-group">
            <div class="area" [style.--area]="row.area.color">
              <span class="area-name">{{ row.area.name }}</span>
              @if (hasCredits()) {
                <span class="area-meta">{{ row.credits }} credits</span>
              }
            </div>
            @for (cell of row.cells; track $index) {
              <div class="cell">
                @for (item of cell; track item.pensumItemCode) {
                  <article class="item" [class.slot]="item.isElectiveSlot" [style.--area]="row.area.color">
                    <div class="item-top">
                      <span class="mono code">{{ item.code ?? item.pensumItemCode }}</span>
                      @if (item.isElectiveSlot) {
                        <span class="tag">elective</span>
                      }
                    </div>
                    <div class="item-name">{{ item.name }}</div>
                    @if (hasCredits() || hasHours()) {
                      <div class="item-meta">{{ figures(item.credits, item.weeklyHours) }}</div>
                    }
                    @if (item.prerequisites.length) {
                      <div class="item-pre" [title]="'Prerequisites: ' + item.prerequisites.join(', ')">
                        needs <span class="mono">{{ item.prerequisites.join(', ') }}</span>
                      </div>
                    }
                  </article>
                }
              </div>
            }
          </div>
        }

        <div class="row-group totals">
          <div class="corner">Per level</div>
          @for (total of levelTotals(); track $index) {
            <div class="level-total">{{ figures(total.credits, total.hours) || (total.items === 1 ? '1 course' : total.items + ' courses') }}</div>
          }
        </div>
      </section>
    </div>
  `,
  styles: `
    .grid {
      --col: 9.5rem;
      display: grid;
      /* Capped, so a two-semester specialisation is not stretched into two page-wide columns. */
      grid-template-columns: 8.5rem repeat(var(--levels), minmax(var(--col), 18rem));
      width: max-content;
      min-width: calc(8.5rem + var(--levels) * var(--col));
      max-width: 100%;
      border: 1px solid var(--border);
      border-radius: var(--radius-md);
      overflow: hidden;
      background: var(--bg-elevated);
    }
    .row-group {
      display: contents;
    }
    .missing {
      margin: 0 0 0.5rem;
    }
    .corner,
    .level-head {
      background: var(--bg-inset);
      color: var(--text-muted);
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      padding: 0.5rem 0.625rem;
      border-bottom: 1px solid var(--border);
    }
    .level-head {
      text-align: center;
    }
    .area {
      display: flex;
      flex-direction: column;
      justify-content: center;
      gap: 0.25rem;
      padding: 0.625rem;
      border-bottom: 1px solid var(--border);
      border-left: 4px solid var(--area);
      background: color-mix(in oklab, var(--area) 12%, var(--bg-elevated));
    }
    .area-name {
      font-weight: 600;
      font-size: 0.8125rem;
      line-height: 1.25;
    }
    .area-meta {
      color: var(--text-muted);
      font-size: 0.75rem;
    }
    .cell {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
      padding: 0.375rem;
      border-bottom: 1px solid var(--border);
      border-left: 1px solid var(--border);
      min-height: 3rem;
    }
    .item {
      border: 1px solid var(--border);
      border-top: 3px solid var(--area);
      border-radius: var(--radius-sm);
      background: var(--bg);
      padding: 0.375rem 0.5rem;
      display: flex;
      flex-direction: column;
      gap: 0.125rem;
    }
    .item.slot {
      border-style: dashed;
      border-top-style: solid;
    }
    .item-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.25rem;
    }
    .code {
      font-size: 0.6875rem;
      color: var(--text-muted);
    }
    .tag {
      font-size: 0.625rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--text-muted);
    }
    .item-name {
      font-size: 0.8125rem;
      font-weight: 500;
      line-height: 1.25;
      color: var(--text);
    }
    .item-meta {
      font-size: 0.75rem;
      color: var(--text-muted);
    }
    .item-pre {
      font-size: 0.6875rem;
      color: var(--text-muted);
      overflow-wrap: anywhere;
    }
    .totals .corner,
    .level-total {
      background: var(--bg-inset);
      border-bottom: 0;
      font-size: 0.75rem;
      color: var(--text-muted);
    }
    .level-total {
      text-align: center;
      padding: 0.5rem 0.25rem;
      border-left: 1px solid var(--border);
    }
  `,
})
export class PensumGridComponent {
  readonly pensum = input.required<Pensum>();

  /** False when the source document prints no per-course credits; every item then holds 0. */
  readonly hasCredits = computed(() => this.pensum().courses.some((c) => c.credits > 0));
  /** False when the source document prints no weekly hours. */
  readonly hasHours = computed(() => this.pensum().courses.some((c) => c.weeklyHours > 0));

  /** Says which figures the source document leaves out, so a missing one never reads as a zero. */
  readonly missingNote = computed(() => {
    const total = this.pensum().totalCredits;
    if (!this.hasCredits() && !this.hasHours()) {
      return `The published plan prints neither credits nor weekly hours per course${total ? ` — only a total of ${total} credits` : ''}.`;
    }
    if (!this.hasCredits()) {
      return `The published plan prints no credits per course${total ? ` — only a total of ${total}` : ''}.`;
    }
    return 'The published plan prints no weekly hours per course.';
  });

  readonly levelLabels = computed(() =>
    Array.from({ length: this.pensum().levels }, (_, i) => ROMAN[i] ?? String(i + 1)),
  );

  readonly rows = computed<GridRow[]>(() => {
    const p = this.pensum();
    return p.areas.map((area) => {
      const items = p.courses.filter((c) => c.area === area.code);
      return {
        area,
        // Spanish collation: the API orders by code points, which files "Ética" after "Violencia".
        cells: Array.from({ length: p.levels }, (_, i) =>
          items.filter((c) => c.level === i + 1).sort((a, b) => a.name.localeCompare(b.name, 'es')),
        ),
        credits: items.reduce((sum, c) => sum + c.credits, 0),
      };
    });
  });

  readonly levelTotals = computed(() => {
    const p = this.pensum();
    return Array.from({ length: p.levels }, (_, i) => {
      const items = p.courses.filter((c) => c.level === i + 1);
      return {
        credits: items.reduce((sum, c) => sum + c.credits, 0),
        hours: items.reduce((sum, c) => sum + c.weeklyHours, 0),
        items: items.length,
      };
    });
  });

  /** "3 cr · 4 h", leaving out whichever figure the plan does not publish. */
  figures(credits: number, hours: number): string {
    return [this.hasCredits() ? `${credits} cr` : '', this.hasHours() ? `${hours} h` : '']
      .filter(Boolean)
      .join(' · ');
  }
}

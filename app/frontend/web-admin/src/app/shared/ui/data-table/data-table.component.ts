import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  OnDestroy,
  Output,
  inject,
  input,
} from '@angular/core';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/**
 * The one table shell every entity list uses: a loading row, an empty-state row, and paging
 * controls that respect whatever page/size/totalPages the API actually returned - none of that
 * chrome should be reinvented per entity, only the columns differ. The caller supplies its own
 * `<thead>` and `<tbody>` as projected content, because column shape (badges, formatted dates,
 * row actions) genuinely differs per entity and forcing it through a generic cell-renderer would
 * be more machinery than the five tables in this app justify.
 */
@Component({
  selector: 'app-data-table',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="data-table">
      <div class="scroll-x">
        <table>
          <ng-content select="thead" />
          @if (loading()) {
            <tbody>
              <tr>
                <td [attr.colspan]="100">
                  <div class="empty-state">{{ 'Loading…' | t }}</div>
                </td>
              </tr>
            </tbody>
          } @else if (empty()) {
            <tbody>
              <tr>
                <td [attr.colspan]="100">
                  <div class="empty-state">{{ emptyMessage() | t }}</div>
                </td>
              </tr>
            </tbody>
          } @else {
            <ng-content select="tbody" />
          }
        </table>
      </div>

      @if (totalPages() > 1) {
        <div class="row-between pagination">
          <span class="text-muted">
            {{ 'Page {value} of {totalPages}' | t: { value: page() + 1, totalPages: totalPages() } }}
            @if (totalItems() !== null) {
              {{ '· {totalItems} total' | t: { totalItems: totalItems() } }}
            }
          </span>
          <div class="row">
            <button type="button" class="btn btn-sm" [disabled]="page() === 0 || loading()" (click)="pageChange.emit(page() - 1)">
              {{ 'Previous' | t }}
            </button>
            <button
              type="button"
              class="btn btn-sm"
              [disabled]="page() >= totalPages() - 1 || loading()"
              (click)="pageChange.emit(page() + 1)"
            >
              {{ 'Next' | t }}
            </button>
          </div>
        </div>
      }
    </div>
  `,
  styles: `
    .pagination {
      padding: 0.75rem 0 0.25rem;
      font-size: 0.8125rem;
    }
  `,
})
export class DataTableComponent implements AfterViewInit, OnDestroy {
  private readonly host = inject(ElementRef<HTMLElement>);
  private observer?: MutationObserver;

  /**
   * Copies each column heading onto its cells as `data-label`.
   *
   * Below 640px the table stops being a table: every row becomes a card, and a value with no
   * label beside it is a guess — "PREGRADO" and "506" mean nothing stacked on their own. The
   * usual fix is writing data-label by hand on every `<td>`, which here would be seven pages
   * and one more thing to forget when a column is added. Doing it from the headings means the
   * labels cannot drift from the table they describe.
   *
   * A MutationObserver rather than a lifecycle hook that runs on every change detection: rows
   * arrive asynchronously and change rarely, so this fires when the data does and not before.
   */
  ngAfterViewInit(): void {
    const table = this.host.nativeElement.querySelector('table');
    if (!table) {
      return;
    }
    this.label(table);
    this.observer = new MutationObserver(() => this.label(table));
    this.observer.observe(table, { childList: true, subtree: true });
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  private label(table: HTMLTableElement): void {
    const headings = [...table.querySelectorAll('thead th')].map((th) => th.textContent?.trim() ?? '');
    if (!headings.length) {
      return;
    }
    for (const row of table.querySelectorAll('tbody tr')) {
      const cells = row.children;

      // The loading and empty states are a single cell spanning the whole table. Its
      // position happens to be the first column's, so labelling by index would print
      // "CODE" next to "No passes yet" - which reads as a value that is not there.
      if (cells.length === 1 && cells[0].hasAttribute('colspan')) {
        continue;
      }

      for (let i = 0; i < cells.length; i++) {
        const heading = headings[i];
        // An action column has no heading; labelling it "" would print an empty line.
        if (heading && !cells[i].hasAttribute('data-label')) {
          cells[i].setAttribute('data-label', heading);
        }
      }
    }
  }

  readonly loading = input(false);
  readonly empty = input(false);
  readonly emptyMessage = input(/* i18n */ 'No results.');
  readonly totalItems = input<number | null>(null);
  readonly page = input(0);
  readonly totalPages = input(0);

  @Output() readonly pageChange = new EventEmitter<number>();
}

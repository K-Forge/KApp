import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { ImportPanelComponent } from '../import/import-panel.component';
import { PastePensumComponent } from '../import/paste-pensum.component';
import { PensumGridComponent } from './pensum-grid.component';
import { PensumsService } from './pensums.service';
import {
  coursesByItemCode,
  officialCode,
  prerequisiteLabels,
  publishesCourseCodes,
  type Pensum,
  type PensumCourse,
  type PensumSummary,
} from './pensum.model';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { t } from '../../../core/i18n/i18n.service';

/**
 * The pensums of the catalog, read-only: a picker over `GET /api/catalog/pensums`, and each plan
 * drawn as printed or as a table, to check it against its PDF.
 *
 * <p>The catalog is SINU's. The one write it takes is the import of its backup, which lives at the
 * bottom of this screen: a pensum arrives, or is corrected, by importing it again.
 */
@Component({
  selector: 'app-pensums-page',
  imports: [TranslatePipe, ApiErrorBannerComponent, ImportPanelComponent, PageIntroComponent, PastePensumComponent, PensumGridComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        [title]="'Pensums' | t"
        [what]="'A programme’s plan of study: its courses, levels, credits and prerequisites.' | t"
        [can]="[('Open one by its code' | t), ('Build one by pasting a PDF’s table' | t), ('Correct one by importing it again' | t)]"
        [note]="'The catalog is SINU’s; these plans are its backup. Correcting one reaches every student.' | t"
      >
      </app-page-intro>

      <div class="card stack">
        <div class="work-bar">
          <div class="field" style="margin-bottom: 0; flex: 1 1 22rem">
            <label for="pensum">{{ 'Pensum' | t }}</label>
            <!-- A list, not a text box. Asking somebody to remember "1015" was asking them to
                 know the answer before the screen could tell them. -->
            <select id="pensum" (change)="onPensumPicked($event)" [disabled]="catalogLoading()">
              <option value="">
                {{ catalogLoading() ? ('Loading the catalogue…' | t) : ('Choose a pensum…' | t) }}
              </option>
              @for (p of catalog(); track p.pensumCode) {
                <option [value]="p.pensumCode" [selected]="p.pensumCode === searchCode()">
                  {{ p.pensumCode }} · {{ p.programName }} · {{ p.reform }}{{ p.status === 'ACTIVE' ? '' : ' (' + p.status + ')' }}
                </option>
              }
            </select>
          </div>
          <button type="button" class="btn" (click)="load()" [disabled]="loading() || !searchCode().trim()">
            {{ loading() ? ('Loading…' | t) : ('Load' | t) }}
          </button>
        </div>

        @if (!catalogLoading() && catalog().length === 0) {
          <p class="hint" style="margin:0">
            {{ 'No pensums yet. Build the first one from its PDF below.' | t }}
          </p>
        }

        <app-api-error-banner [error]="error()" />

        @if (!loading() && !error() && !loaded()) {
          <div class="empty-state">
            <p>{{ 'Choose one above to read it.' | t }}</p>
          </div>
        }

        @if (loaded(); as c) {
          <div class="stack">
            <h2 style="margin:0">{{ c.programName }} · {{ c.pensumCode }}</h2>
            <dl class="pensum-summary">
              <dt>{{ 'Faculty' | t }}</dt>
              <dd>{{ c.faculty }}</dd>
              <dt>{{ 'Reform' | t }}</dt>
              <dd>{{ c.reform }}</dd>
              <dt>{{ 'Status' | t }}</dt>
              <dd><span class="badge badge-neutral">{{ c.status }}</span></dd>
              <dt>{{ 'Totals' | t }}</dt>
              <dd>{{ totals(c) }}</dd>
              <dt>{{ 'Course codes' | t }}</dt>
              <dd>{{ codesNote(c) }}</dd>
            </dl>

            <h3>{{ 'Knowledge areas ({length})' | t: { length: c.areas.length } }}</h3>
            <div class="scroll-x">
              <table>
                <thead>
                  <tr>
                    <th>{{ 'Code' | t }}</th>
                    <th>{{ 'Name' | t }}</th>
                    <th>{{ 'Credits' | t }}</th>
                    <th>{{ 'Weekly hours' | t }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (area of c.areas; track area.code) {
                    <tr>
                      <td class="mono">{{ area.code }}</td>
                      <td>
                        <span class="area-dot" [style.background]="area.color"></span>
                        {{ area.name }}
                      </td>
                      <td>{{ published(c, 'credits') ? area.credits : '—' }}</td>
                      <td>{{ published(c, 'hours') ? area.hours : '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>

            <div class="row-between">
              <h3 style="margin:0">{{ 'Pensum items ({length})' | t: { length: c.courses.length } }}</h3>
              <!-- The grid is for checking a pensum against its printed plan; the table is the
                   exact data, sortable by eye and readable by a screen reader. -->
              <div class="view-toggle" role="group" [attr.aria-label]="'How to show the items' | t">
                <button type="button" class="btn btn-sm" [attr.aria-pressed]="view() === 'grid'" (click)="view.set('grid')">
                  {{ 'As printed' | t }}
                </button>
                <button type="button" class="btn btn-sm" [attr.aria-pressed]="view() === 'table'" (click)="view.set('table')">
                  {{ 'Table' | t }}
                </button>
              </div>
            </div>
            @if (view() === 'grid') {
              <app-pensum-grid [pensum]="c" />
            } @else {
              <div class="scroll-x table-scroll-y">
                <table>
                  <thead>
                    <tr>
                      <th>{{ 'Level' | t }}</th>
                      <th>{{ 'Code' | t }}</th>
                      <th>{{ 'Name' | t }}</th>
                      <th>{{ 'Area' | t }}</th>
                      <th>{{ 'Credits' | t }}</th>
                      <th>{{ 'Weekly hours' | t }}</th>
                      <th>{{ 'Prerequisites' | t }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (course of c.courses; track course.pensumItemCode) {
                      <tr>
                        <td>{{ course.level }}</td>
                        <!-- The university's own code or nothing: the internal identifier KApp
                             gives an item of a plan that prints none is not shown anywhere. -->
                        <td class="mono">
                          {{ code(course) ?? '—' }}
                          @if (course.isElectiveSlot) {
                            <span class="text-muted">{{ '(elective)' | t }}</span>
                          }
                        </td>
                        <td>{{ course.name }}</td>
                        <td>{{ course.area }}</td>
                        <td>{{ published(c, 'credits') ? course.credits : '—' }}</td>
                        <td>{{ published(c, 'hours') ? course.weeklyHours : '—' }}</td>
                        <td class="text-muted">{{ prerequisites(course) || '—' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        }
      </div>

      <!-- The catalog's only write: its backup, imported. It lives here rather than in its own
           navigation entry because it is how a pensum on this screen arrives or is corrected. -->
      <!-- Creating a pensum, at the scale the pensums actually arrive: as PDFs, twenty-four
           of them. Paste is the primary path because copying a table out of a PDF preserves
           its rows better than any attempt to reconstruct the page, and because whoever does
           it has to check every row anyway. -->
      <details class="card import-panel" open>
        <summary>
          <strong>{{ 'Build one from a PDF' | t }}</strong>
          <span class="text-muted"> {{ '— paste the table, correct it, import it' | t }}</span>
        </summary>
        <div style="margin-top:1rem">
          <app-paste-pensum (imported)="loadCatalog()" />
        </div>
      </details>

      <details class="card import-panel">
        <summary>
          <strong>{{ 'Already have a CSV?' | t }}</strong>
          <span class="text-muted"> {{ '— upload it directly' | t }}</span>
        </summary>
        <div style="margin-top:1rem">
          <app-import-panel />
        </div>
      </details>
    </div>

  `,
  styles: `
    .import-panel > summary {
      cursor: pointer;
      list-style: none;
      font-size: 0.9375rem;
    }
    .import-panel > summary::-webkit-details-marker {
      display: none;
    }
    .import-panel > summary::before {
      content: '▸ ';
      color: var(--text-muted);
    }
    .import-panel[open] > summary::before {
      content: '▾ ';
    }

    .pensum-summary {
      display: grid;
      grid-template-columns: 8rem 1fr;
      row-gap: 0.5rem;
      margin: 0;
    }
    .pensum-summary dt {
      color: var(--text-muted);
      font-size: 0.8125rem;
      font-weight: 600;
    }
    .pensum-summary dd {
      margin: 0;
    }
    .area-dot {
      display: inline-block;
      width: 0.6rem;
      height: 0.6rem;
      border-radius: 999px;
      margin-right: 0.4rem;
    }
    .table-scroll-y {
      max-height: 22rem;
      overflow-y: auto;
    }
    .view-toggle {
      display: inline-flex;
    }
    .view-toggle .btn {
      border-radius: 0;
    }
    .view-toggle .btn:first-child {
      border-radius: var(--radius-sm) 0 0 var(--radius-sm);
    }
    .view-toggle .btn:last-child {
      border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
      margin-left: -1px;
    }
    .view-toggle .btn[aria-pressed='true'] {
      background: var(--nav-active-bg);
      color: var(--nav-active-text);
      border-color: var(--border-strong);
    }
  `,
})
export class PensumsPage {
  private readonly pensumsService = inject(PensumsService);

  /** Bound automatically from ?pensum=... via withComponentInputBinding (see Programs "View pensum"). */
  readonly pensum = input('');

  /** Every pensum, for the picker. Summaries only; the document is fetched on demand. */
  readonly catalog = signal<PensumSummary[]>([]);
  readonly catalogLoading = signal(true);

  readonly searchCode = signal('');
  readonly loading = signal(false);
  readonly error = signal<ApiError | null>(null);
  /** The pensum currently on screen. Null until one is loaded. */
  readonly loaded = signal<Pensum | null>(null);
  /**
   * A plan whose document prints no credits (or no hours) stores zeros for them. Zeros on
   * screen would read as "worth nothing", so those columns show a dash instead - the grid
   * says in words which figure the document leaves out.
   */
  published(pensum: Pensum, figure: 'credits' | 'hours'): boolean {
    return figure === 'credits'
      ? pensum.courses.some((course) => course.credits > 0)
      : pensum.courses.some((course) => course.weeklyHours > 0);
  }

  totals(pensum: Pensum): string {
    const credits = this.published(pensum, 'credits')
      ? t('{credits} credits', { credits: pensum.totalCredits })
      : pensum.totalCredits
        ? t('{credits} credits in total, none per course', { credits: pensum.totalCredits })
        : t('credits not published');
    return [
      credits,
      this.published(pensum, 'hours') ? t('{hours} weekly hours', { hours: pensum.totalHours }) : '',
      t('{levels} levels', { levels: pensum.levels }),
    ]
      .filter(Boolean)
      .join(' · ');
  }

  /**
   * The code to put on screen for an item, or null.
   *
   * <p>Only four of the twenty-three published plans print course codes. For the rest KApp
   * generates one so the system can tell the items apart, and that one is never shown: it looks
   * institutional, it is not, and it changes the day the real codes arrive.
   */
  code(course: PensumCourse): string | null {
    return officialCode(course);
  }

  /**
   * The loaded pensum indexed by the code its prerequisites name items with. A computed rather
   * than a call per row: the table renders sixty rows and the index would be rebuilt for each.
   */
  private readonly byCode = computed(() => {
    const pensum = this.loaded();
    return pensum ? coursesByItemCode(pensum) : new Map<string, PensumCourse>();
  });

  /** Prerequisites as they are shown: by code where the plan has real ones, by name where not. */
  prerequisites(course: PensumCourse): string {
    return prerequisiteLabels(this.byCode(), course).join(', ');
  }

  codesNote(pensum: Pensum): string {
    return publishesCourseCodes(pensum)
      ? t('The university’s own, as the plan prints them.')
      : t('Not published. KApp identifies these items internally; those identifiers are not institutional codes and are not shown.');
  }

  /** How the items are shown. The grid first: it is the view a pensum is checked against its PDF with. */
  readonly view = signal<'grid' | 'table'>('grid');

  constructor() {
    this.loadCatalog();
    // A signal input set by withComponentInputBinding (see app.config.ts) is not populated yet
    // when the constructor body runs - the router calls setInput() on the component instance
    // right after construction, not before it. Reading this.pensum() here once would silently
    // see only the default '', which is exactly the kind of bug that only shows up on a deep
    // link (Programs' "View pensum" button) and never in a normal click-through. An effect
    // re-reads the signal once the router actually sets it.
    effect(() => {
      const code = this.pensum();
      if (code) {
        this.searchCode.set(code);
        this.fetchPensum(code);
      }
    });
  }

  /** Picking one loads it. Keeping Load as well, for re-reading the same pensum after an edit. */
  onPensumPicked(event: Event): void {
    const code = (event.target as HTMLSelectElement).value;
    this.searchCode.set(code);
    if (code) {
      this.fetchPensum(code);
    } else {
      this.loaded.set(null);
      this.error.set(null);
    }
  }

  /** Re-reads the catalogue, so a pensum just imported shows up in the picker. */
  loadCatalog(): void {
    this.catalogLoading.set(true);
    this.pensumsService.list().subscribe({
      next: (pensums) => {
        this.catalog.set(pensums);
        this.catalogLoading.set(false);
      },
      error: (err: unknown) => {
        this.catalogLoading.set(false);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }

  load(): void {
    this.fetchPensum(this.searchCode().trim());
  }

  /**
   * Takes the code as a parameter rather than reading this.searchCode() internally, so the
   * effect above can call it without also making the effect re-run on every keystroke in the
   * search box (an effect tracks every signal it reads during its callback, transitively).
   */
  private fetchPensum(code: string): void {
    if (!code) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.pensumsService.getByCode(code).subscribe({
      next: (pensum) => {
        this.loaded.set(pensum);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loaded.set(null);
        this.loading.set(false);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }
}

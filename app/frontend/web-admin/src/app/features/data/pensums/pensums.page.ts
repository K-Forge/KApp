import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { ChangeDetectionStrategy, Component, ViewChild, effect, inject, input, signal } from '@angular/core';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { ImportPanelComponent } from '../import/import-panel.component';
import { PastePensumComponent } from '../import/paste-pensum.component';
import { PensumGridComponent } from './pensum-grid.component';
import { PensumsService } from './pensums.service';
import { PENSUM_SKELETON, type Pensum, type PensumSummary } from './pensum.model';

/**
 * Lookup-by-code rather than a table: the semaphore contract has no "list pensums" endpoint,
 * only get/create/replace by pensumCode (see docs/api/semaphore.openapi.yaml). A pensum document
 * nests ~50 course items, so create/replace edit the whole document as JSON rather than forcing
 * every field through bespoke inputs - the same trade-off the API console makes for request
 * bodies, and for the same reason: the shape is defined by the schema, not reinvented here.
 */
@Component({
  selector: 'app-pensums-page',
  imports: [ApiErrorBannerComponent, ModalComponent, ImportPanelComponent, PageIntroComponent, PastePensumComponent, PensumGridComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        title="Pensums"
        what="A pensum is one version of a programme&#39;s plan of study: its courses, their levels, their credits and which ones unlock which."
        [can]="['Load one by its code', 'Build one from a PDF by pasting its table', 'Edit the whole document', 'Delete one no student is following']"
        note="The pensum is immutable as far as students are concerned — a student&#39;s own rearrangement is a separate plan layered over it, so correcting a pensum here reaches every student without rewriting anybody&#39;s plan. Deleting never cascades: one that students are following is refused with a 409 saying how many."
      >
        <button actions type="button" class="btn btn-primary" (click)="openCreate()">New pensum</button>
      </app-page-intro>

      <div class="card stack">
        <div class="row" style="align-items: end">
          <div class="field" style="margin-bottom: 0; flex: 1 1 22rem">
            <label for="pensum">Pensum</label>
            <!-- A list, not a text box. Asking somebody to remember "1015" was asking them to
                 know the answer before the screen could tell them. -->
            <select id="pensum" (change)="onPensumPicked($event)" [disabled]="catalogLoading()">
              <option value="">
                {{ catalogLoading() ? 'Loading the catalogue…' : 'Choose a pensum…' }}
              </option>
              @for (p of catalog(); track p.pensumCode) {
                <option [value]="p.pensumCode" [selected]="p.pensumCode === searchCode()">
                  {{ p.pensumCode }} · {{ p.programName }} · {{ p.reform }}{{ p.status === 'ACTIVE' ? '' : ' (' + p.status + ')' }}
                </option>
              }
            </select>
          </div>
          <button type="button" class="btn" (click)="load()" [disabled]="loading() || !searchCode().trim()">
            {{ loading() ? 'Loading…' : 'Load' }}
          </button>
        </div>

        @if (!catalogLoading() && catalog().length === 0) {
          <p class="hint" style="margin:0">
            No pensums yet. Build the first one from its PDF below.
          </p>
        }

        <app-api-error-banner [error]="error()" />

        @if (!loading() && !error() && !loaded()) {
          <div class="empty-state">
            <p>Choose one above to read it, edit it or delete it.</p>
          </div>
        }

        @if (loaded(); as c) {
          <div class="stack">
            <div class="row-between">
              <h2 style="margin:0">{{ c.programName }} · {{ c.pensumCode }}</h2>
              <div class="row">
                <button type="button" class="btn btn-sm" (click)="openEdit(c)">Edit this pensum</button>
                <button type="button" class="btn btn-sm btn-danger" [disabled]="deleting()" (click)="remove(c)">
                  {{ deleting() ? 'Deleting…' : 'Delete' }}
                </button>
              </div>
            </div>
            <dl class="pensum-summary">
              <dt>Faculty</dt>
              <dd>{{ c.faculty }}</dd>
              <dt>Reform</dt>
              <dd>{{ c.reform }}</dd>
              <dt>Status</dt>
              <dd><span class="badge badge-neutral">{{ c.status }}</span></dd>
              <dt>Totals</dt>
              <dd>{{ c.totalCredits }} credits · {{ c.totalHours }} weekly hours · {{ c.levels }} levels</dd>
            </dl>

            <h3>Knowledge areas ({{ c.areas.length }})</h3>
            <div class="scroll-x">
              <table>
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Name</th>
                    <th>Credits</th>
                    <th>Weekly hours</th>
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
                      <td>{{ area.credits }}</td>
                      <td>{{ area.hours }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>

            <div class="row-between">
              <h3 style="margin:0">Pensum items ({{ c.courses.length }})</h3>
              <!-- The grid is for checking a pensum against its printed plan; the table is the
                   exact data, sortable by eye and readable by a screen reader. -->
              <div class="view-toggle" role="group" aria-label="How to show the items">
                <button type="button" class="btn btn-sm" [attr.aria-pressed]="view() === 'grid'" (click)="view.set('grid')">
                  As printed
                </button>
                <button type="button" class="btn btn-sm" [attr.aria-pressed]="view() === 'table'" (click)="view.set('table')">
                  Table
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
                      <th>Level</th>
                      <th>Code</th>
                      <th>Name</th>
                      <th>Area</th>
                      <th>Credits</th>
                      <th>Weekly hours</th>
                      <th>Prerequisites</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (course of c.courses; track course.pensumItemCode) {
                      <tr>
                        <td>{{ course.level }}</td>
                        <td class="mono">
                          @if (course.isElectiveSlot) {
                            {{ course.pensumItemCode }} <span class="text-muted">(elective)</span>
                          } @else {
                            {{ course.code }}
                          }
                        </td>
                        <td>{{ course.name }}</td>
                        <td>{{ course.area }}</td>
                        <td>{{ course.credits }}</td>
                        <td>{{ course.weeklyHours }}</td>
                        <td class="text-muted">{{ course.prerequisites.length ? course.prerequisites.join(', ') : '—' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        }
      </div>

      <!-- Bulk creation. It lives here rather than in its own navigation entry because
           importing a CSV IS the create half of this screen's CRUD - twenty-four pensums is
           not something anybody types in one at a time. -->
      <!-- Creating a pensum, at the scale the pensums actually arrive: as PDFs, twenty-four
           of them. Paste is the primary path because copying a table out of a PDF preserves
           its rows better than any attempt to reconstruct the page, and because whoever does
           it has to check every row anyway. -->
      <details class="card import-panel" open>
        <summary>
          <strong>Build one from a PDF</strong>
          <span class="text-muted"> — paste the table, correct it, import it</span>
        </summary>
        <div style="margin-top:1rem">
          <app-paste-pensum (imported)="loadCatalog()" />
        </div>
      </details>

      <details class="card import-panel">
        <summary>
          <strong>Already have a CSV?</strong>
          <span class="text-muted"> — upload it directly</span>
        </summary>
        <div style="margin-top:1rem">
          <app-import-panel />
        </div>
      </details>
    </div>

    <app-modal #formModal [title]="formMode() === 'create' ? 'New pensum' : 'Edit pensum'" (closed)="formError.set(null)">
      <div class="stack">
        <app-api-error-banner [error]="formError()" />
        <p class="text-muted">
          The whole pensum as JSON, matching the <code>Pensum</code> schema in
          <code>docs/api/semaphore.openapi.yaml</code> — which is still what the contract calls it.
        </p>
        <div class="field">
          <label for="pensum-json">Pensum document</label>
          <textarea id="pensum-json" rows="16" [value]="formText()" (input)="onFormTextInput($event)"></textarea>
        </div>
        <div class="row">
          <button type="button" class="btn btn-primary" [disabled]="formSubmitting()" (click)="submit()">
            {{ formSubmitting() ? 'Saving…' : formMode() === 'create' ? 'Create pensum' : 'Save changes' }}
          </button>
          <button type="button" class="btn" (click)="formModal.close()">Cancel</button>
        </div>
      </div>
    </app-modal>
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
  readonly deleting = signal(false);
  /** How the items are shown. The grid first: it is the view a pensum is checked against its PDF with. */
  readonly view = signal<'grid' | 'table'>('grid');

  readonly formMode = signal<'create' | 'edit'>('create');
  readonly formText = signal('');
  readonly formSubmitting = signal(false);
  readonly formError = signal<ApiError | null>(null);

  @ViewChild('formModal') private formModal?: ModalComponent;

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

  /** Re-reads the catalogue, so a pensum just imported or deleted shows up in the picker. */
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

  openCreate(): void {
    this.formMode.set('create');
    this.formText.set(JSON.stringify(PENSUM_SKELETON, null, 2));
    this.formError.set(null);
    this.formModal?.open();
  }

  openEdit(pensum: Pensum): void {
    this.formMode.set('edit');
    this.formText.set(JSON.stringify(pensum, null, 2));
    this.formError.set(null);
    this.formModal?.open();
  }

  onFormTextInput(event: Event): void {
    this.formText.set((event.target as HTMLTextAreaElement).value);
  }

  submit(): void {
    let parsed: Pensum;
    try {
      parsed = JSON.parse(this.formText());
    } catch {
      this.formError.set({
        timestamp: new Date().toISOString(),
        status: 0,
        error: 'Invalid JSON',
        message: 'The pensum document is not valid JSON.',
        path: '',
      });
      return;
    }

    this.formSubmitting.set(true);
    this.formError.set(null);
    const call =
      this.formMode() === 'create' ? this.pensumsService.create(parsed) : this.pensumsService.replace(parsed.pensumCode, parsed);

    call.subscribe({
      next: (saved) => {
        this.formSubmitting.set(false);
        this.formModal?.close();
        this.loaded.set(saved);
        this.searchCode.set(saved.pensumCode);
        this.loadCatalog();
      },
      error: (err: unknown) => {
        this.formSubmitting.set(false);
        this.formError.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }

  /**
   * Deleting never cascades. A pensum students are following comes back as `409` saying how many,
   * which the error banner renders from the envelope's own details - the confirmation says so up
   * front rather than letting the refusal look like a bug.
   */
  remove(pensum: Pensum): void {
    if (
      !window.confirm(
        `Delete pensum ${pensum.pensumCode} (${pensum.programName})? ` +
          `It has ${pensum.courses.length} courses. If any student is following it, the ` +
          `server refuses and says how many.`,
      )
    ) {
      return;
    }
    this.deleting.set(true);
    this.error.set(null);
    this.pensumsService.delete(pensum.pensumCode).subscribe({
      next: () => {
        this.deleting.set(false);
        this.loaded.set(null);
        this.searchCode.set('');
        this.loadCatalog();
      },
      error: (err: unknown) => {
        this.deleting.set(false);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }
}

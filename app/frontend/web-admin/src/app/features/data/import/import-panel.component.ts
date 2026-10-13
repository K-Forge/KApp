import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import type { PensumImportReport } from './import.model';
import { PensumImportService } from './import.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { t } from '../../../core/i18n/i18n.service';

/**
 * Bulk loading of pensums from a spreadsheet export, as a panel rather than a page.
 *
 * <p>It sits inside the Pensums screen because importing a CSV IS the create half of that
 * screen's CRUD: twenty-four programmes and roughly 1200 rows is not something anybody enters
 * one at a time, and a separate "Import" entry in the navigation made it look like a different
 * feature rather than the same one at scale.
 */
@Component({
  selector: 'app-import-panel',
  imports: [TranslatePipe, ApiErrorBannerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <div class="card stack">
        <div class="field">
          <label for="csv">{{ 'CSV file' | t }}</label>
          <input id="csv" type="file" accept=".csv,text/csv" (change)="onFile($event)" />
          @if (file(); as f) {
            <span class="hint">{{ '{name} — {value} KB' | t: { name: f.name, value: (f.size / 1024).toFixed(1) } }}</span>
          } @else {
            <span class="hint">{{ 'UTF-8, at most 5 MB.' | t }}</span>
          }
        </div>

        <div class="row">
          <button type="button" class="btn btn-primary" [disabled]="!file() || busy()" (click)="run(true)">
            {{ busy() && lastWasDryRun() ? ('Checking…' | t) : ('Check the file' | t) }}
          </button>
          <button type="button" class="btn" [disabled]="!file() || busy()" (click)="run(false)">
            {{ busy() && !lastWasDryRun() ? ('Importing…' | t) : ('Import for real' | t) }}
          </button>
        </div>

        <p class="hint" style="margin:0">
          {{ 'Checking validates the whole file and writes nothing. Importing writes only if every row passes — there is no partial import.' | t }}
        </p>
      </div>

      <app-api-error-banner [error]="error()" />
      @if (error()?.status === 400) {
        <p class="text-muted">
          {{ 'Nothing was written. Each entry above is labelled with the line of your file, or the pensum it belongs to. Fix them in the spreadsheet and check again.' | t }}
        </p>
      }

      @if (report(); as r) {
        <div class="card stack">
          <div class="row-between">
            <h2 style="margin:0">
              {{ r.dryRun ? ('The file is valid' | t) : ('Imported' | t) }}
            </h2>
            <span class="badge" [class.badge-neutral]="r.dryRun" [class.badge-ok]="!r.dryRun">
              {{ r.dryRun ? ('nothing was written' | t) : ('written' | t) }}
            </span>
          </div>
          <p class="text-muted" style="margin:0">
            {{ '{rowsRead} data rows, {length} pensum(s).' | t: { rowsRead: r.rowsRead, length: r.pensums.length } }}
          </p>

          <table class="table">
            <thead>
              <tr>
                <th>{{ 'Pensum' | t }}</th>
                <th>{{ 'Program' | t }}</th>
                <th>{{ 'Courses' | t }}</th>
                <th>{{ 'Credits' | t }}</th>
                <th>{{ 'Weekly hours' | t }}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (c of r.pensums; track c.pensumCode) {
                <tr>
                  <td class="mono">{{ c.pensumCode }}</td>
                  <td>{{ c.programCode }} — {{ c.programName }}</td>
                  <td>{{ c.courses }}</td>
                  <td>{{ c.computedCredits }}</td>
                  <td>{{ c.computedHours }}</td>
                  <td class="text-muted">
                    {{ c.pensumCreated ? ('new' | t) : ('replaced' | t) }}
                  </td>
                </tr>
              }
            </tbody>
          </table>

          <p class="hint" style="margin:0">
            {{ 'Credits and hours shown are what the courses actually add up to. The import refuses a file whose declared totals disagree with them — that check already found the seeded Ingeniería de Sistemas plan declaring 142 credits where its 48 courses give 144.' | t }}
          </p>
        </div>
      }
    </div>
  `,
})
export class ImportPanelComponent {
  private readonly service = inject(PensumImportService);

  readonly file = signal<File | null>(null);
  readonly busy = signal(false);
  readonly lastWasDryRun = signal(true);
  readonly error = signal<ApiError | null>(null);
  readonly report = signal<PensumImportReport | null>(null);

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
    this.report.set(null);
    this.error.set(null);
  }

  run(dryRun: boolean): void {
    const file = this.file();
    if (!file) {
      return;
    }
    if (!dryRun && !window.confirm(t('Import {file} for real? Existing pensums with the same codes are replaced.', { file: file.name }))) {
      return;
    }

    this.busy.set(true);
    this.lastWasDryRun.set(dryRun);
    this.error.set(null);
    this.report.set(null);

    this.service.upload(file, dryRun).subscribe({
      next: (report) => {
        this.busy.set(false);
        this.report.set(report);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }
}

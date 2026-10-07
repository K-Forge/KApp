import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { DataTableComponent } from '../../../shared/ui/data-table/data-table.component';
import type { Program } from './program.model';
import { ProgramsService } from './programs.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/**
 * The degree programmes of the catalog, read-only.
 *
 * <p>The catalog is SINU's, and semaphore 2.0 takes no writes to it but the import of its backup:
 * a programme arrives, or changes, with its pensum, on the Pensums screen.
 */
@Component({
  selector: 'app-programs-page',
  imports: [TranslatePipe, DataTableComponent, ApiErrorBannerComponent, RouterLink, PageIntroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        [title]="'Programs' | t"
        [what]="'The degree programmes; their courses live in their pensum.' | t"
        [can]="[('See every programme and its active pensum' | t), ('Open a programme’s active pensum' | t)]"
        [note]="'The catalog is SINU’s, and read-only here. A programme arrives, or changes, by importing its pensum.' | t"
      >
      </app-page-intro>

      <div class="card stack">
        <app-api-error-banner [error]="error()" />

        <app-data-table
          [loading]="loading()"
          [empty]="!loading() && !error() && programs().length === 0"
          [emptyMessage]="'No programs yet.' | t"
        >
          <thead>
            <tr>
              <th>{{ 'Code' | t }}</th>
              <th>{{ 'Name' | t }}</th>
              <th>{{ 'Faculty' | t }}</th>
              <th>{{ 'Level' | t }}</th>
              <th>{{ 'Active pensum' | t }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (program of programs(); track program.code) {
              <tr>
                <td class="mono">{{ program.code }}</td>
                <td>{{ program.name }}</td>
                <td class="text-muted">{{ program.faculty }}</td>
                <td><span class="badge badge-neutral">{{ program.level }}</span></td>
                <td class="mono">{{ program.activePensumCode ?? '—' }}</td>
                <td class="row">
                  @if (program.activePensumCode) {
                    <a class="btn btn-sm" [routerLink]="['/data/pensums']" [queryParams]="{ pensum: program.activePensumCode }">{{ 'Pensum' | t }}</a>
                  }
                </td>
              </tr>
            }
          </tbody>
        </app-data-table>
      </div>
    </div>
  `,
})
export class ProgramsPage {
  private readonly programsService = inject(ProgramsService);

  readonly programs = signal<Program[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiError | null>(null);

  constructor() {
    this.programsService.list().subscribe({
      next: (programs) => {
        this.programs.set(programs);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }
}

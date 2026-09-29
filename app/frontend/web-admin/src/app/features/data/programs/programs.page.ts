import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { ChangeDetectionStrategy, Component, ViewChild, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { DataTableComponent } from '../../../shared/ui/data-table/data-table.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { PROGRAM_LEVELS, type Program, type ProgramRequest } from './program.model';
import { ProgramsService } from './programs.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { t } from '../../../core/i18n/i18n.service';

/**
 * Full CRUD over /api/catalog/programs.
 *
 * <p>This screen was read-only until the contract grew its admin endpoints, and said so. It does
 * not any more.
 *
 * <p>A program's code cannot be changed once created: it is the key every pensum and every
 * student profile points at, and editing it here would silently orphan all of them. Delete the
 * program and create a new one if the code itself is wrong — which the server will refuse while
 * pensums still reference it, naming them.
 */
@Component({
  selector: 'app-programs-page',
  imports: [TranslatePipe, DataTableComponent, ApiErrorBannerComponent, ModalComponent, ReactiveFormsModule, RouterLink, PageIntroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        [title]="'Programs' | t"
        [what]="'The degree programmes the university offers. A programme is the container; the courses live in its pensum.' | t"
        [can]="[('Create a programme' | t), ('Edit its name, faculty and level' | t), ('Delete one that has no pensum yet' | t), ('Jump to its active pensum' | t)]"
        [note]="'A programme’s code cannot be changed once it exists: every pensum and every student profile points at it. Deleting never cascades either — a programme that still has a pensum is refused with a 409 naming which one.' | t"
      >
        <button actions type="button" class="btn btn-primary" (click)="openCreate()">{{ 'New program' | t }}</button>
      </app-page-intro>

      <div class="card">
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
                  <button type="button" class="btn btn-sm" (click)="openEdit(program)">{{ 'Edit' | t }}</button>
                  <button
                    type="button"
                    class="btn btn-sm btn-danger"
                    [disabled]="busyCode() === program.code"
                    (click)="remove(program)"
                  >
                    {{ 'Delete' | t }}
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </app-data-table>
      </div>
    </div>

    <app-modal #formModal [title]="editing() ? ('Edit program' | t) : ('New program' | t)" (closed)="formError.set(null)">
      <app-api-error-banner [error]="formError()" />
      <form [formGroup]="form" (ngSubmit)="save()" class="stack">
        <div class="field" [class.invalid]="invalid('code')">
          <label for="p-code">{{ 'Code' | t }}</label>
          <input id="p-code" type="text" formControlName="code" [readonly]="editing()" />
          @if (editing()) {
            <span class="hint">
              {{ 'Fixed. Every pensum and student profile points at this code.' | t }}
            </span>
          }
          @if (invalid('code')) {
            <span class="error">{{ 'Required, 1-20 characters.' | t }}</span>
          }
        </div>

        <div class="field" [class.invalid]="invalid('name')">
          <label for="p-name">{{ 'Name' | t }}</label>
          <input id="p-name" type="text" formControlName="name" />
          @if (invalid('name')) {
            <span class="error">{{ 'Required, 1-120 characters.' | t }}</span>
          }
        </div>

        <div class="field" [class.invalid]="invalid('faculty')">
          <label for="p-faculty">{{ 'Faculty' | t }}</label>
          <input id="p-faculty" type="text" formControlName="faculty" />
          @if (invalid('faculty')) {
            <span class="error">{{ 'Required, 1-120 characters.' | t }}</span>
          }
        </div>

        <div class="field">
          <label for="p-level">{{ 'Level' | t }}</label>
          <select id="p-level" formControlName="level">
            @for (level of levels; track level) {
              <option [value]="level">{{ level }}</option>
            }
          </select>
        </div>

        <div class="row">
          <button type="submit" class="btn btn-primary" [disabled]="submitting()">
            {{ submitting() ? ('Saving…' | t) : editing() ? ('Save changes' | t) : ('Create program' | t) }}
          </button>
          <button type="button" class="btn" (click)="formModal.close()">{{ 'Cancel' | t }}</button>
        </div>
      </form>
    </app-modal>
  `,
})
export class ProgramsPage {
  private readonly programsService = inject(ProgramsService);

  readonly levels = PROGRAM_LEVELS;

  readonly loading = signal(true);
  readonly error = signal<ApiError | null>(null);
  readonly programs = signal<Program[]>([]);
  readonly busyCode = signal<string | null>(null);

  readonly editingProgram = signal<Program | null>(null);
  readonly submitting = signal(false);
  readonly formError = signal<ApiError | null>(null);

  readonly editing = () => this.editingProgram() !== null;

  @ViewChild('formModal') private formModal?: ModalComponent;

  form = this.buildForm(null);

  constructor() {
    this.fetch();
  }

  private buildForm(program: Program | null) {
    return new FormGroup({
      code: new FormControl(program?.code ?? '', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(1), Validators.maxLength(20)],
      }),
      name: new FormControl(program?.name ?? '', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(1), Validators.maxLength(120)],
      }),
      faculty: new FormControl(program?.faculty ?? '', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(1), Validators.maxLength(120)],
      }),
      level: new FormControl<Program['level']>(program?.level ?? 'PREGRADO', {
        nonNullable: true,
        validators: [Validators.required],
      }),
    });
  }

  invalid(name: 'code' | 'name' | 'faculty'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  private fetch(): void {
    this.loading.set(true);
    this.error.set(null);
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

  openCreate(): void {
    this.editingProgram.set(null);
    this.form = this.buildForm(null);
    this.formError.set(null);
    this.formModal?.open();
  }

  openEdit(program: Program): void {
    this.editingProgram.set(program);
    this.form = this.buildForm(program);
    this.formError.set(null);
    this.formModal?.open();
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.formError.set(null);

    const request = this.form.getRawValue() as ProgramRequest;
    const editingProgram = this.editingProgram();
    const call = editingProgram
      ? this.programsService.replace(editingProgram.code, request)
      : this.programsService.create(request);

    call.subscribe({
      next: () => {
        this.submitting.set(false);
        this.formModal?.close();
        this.fetch();
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.formError.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }

  remove(program: Program): void {
    if (!window.confirm(t('Delete program {code} — {name}? Deleting never cascades: if it still has pensums, the server refuses and says which.', { code: program.code, name: program.name }))) {
      return;
    }
    this.busyCode.set(program.code);
    this.error.set(null);
    this.programsService.delete(program.code).subscribe({
      next: () => {
        this.busyCode.set(null);
        this.fetch();
      },
      error: (err: unknown) => {
        this.busyCode.set(null);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }
}

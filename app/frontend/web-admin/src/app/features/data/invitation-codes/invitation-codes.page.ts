import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { ChangeDetectionStrategy, Component, ViewChild, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { DataTableComponent } from '../../../shared/ui/data-table/data-table.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import {
  INVITATION_ROLES,
  remainingUses,
  type InvitationCode,
  type InvitationRole,
} from './invitation-code.model';
import { InvitationCodesService } from './invitation-codes.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { t } from '../../../core/i18n/i18n.service';
import { LocalDatePipe } from '../../../core/i18n/local-date.pipe';

/**
 * The codes that gate institutional registration.
 *
 * <p>These are the only door into KApp that is not a university identity, so seeing which exist,
 * how many uses each has left and which are still redeemable is the difference between managing
 * access and guessing at it.
 *
 * <p>A code cannot be edited, only deactivated or deleted. Its role and quota are what the people
 * already holding it were told; changing either retroactively would rewrite that.
 */
@Component({
  selector: 'app-invitation-codes-page',
  imports: [TranslatePipe, LocalDatePipe, DataTableComponent, ApiErrorBannerComponent, ModalComponent, ReactiveFormsModule, PageIntroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        [title]="'Invitation codes' | t"
        [what]="'How a student or a professor creates their own KApp account in the mobile app. The code they type decides which role they get.' | t"
        [can]="[('Create a code for an intake' | t), ('Set how many accounts it may create, and when it expires' | t), ('Deactivate one without losing its history' | t), ('Delete one entirely' | t)]"
        [note]="'Not the same thing as a visitor pass. A code creates a permanent account for somebody who belongs to the university; a visitor pass is a 24-hour token for somebody who does not, and creates no account at all. ROLE_ADMIN is never grantable by a code — the seeded ones ship in a public repository, so a code that could mint an administrator would let anyone who can read it escalate.' | t"
      >
        <button actions type="button" class="btn btn-primary" (click)="openCreate()">{{ 'New code' | t }}</button>
      </app-page-intro>

      @if (justMinted(); as code) {
        <div class="card issued" role="status">
          <p style="margin:0 0 0.25rem">{{ 'New code — give this to the intake:' | t }}</p>
          <p class="issued-code mono">{{ code }}</p>
          <button type="button" class="btn btn-sm" (click)="justMinted.set(null)">{{ 'Dismiss' | t }}</button>
        </div>
      }

      <div class="card stack">
        <div class="field" style="margin-bottom:0; max-width: 14rem">
          <label for="ic-filter">{{ 'Status' | t }}</label>
          <select id="ic-filter" (change)="onFilter($event)">
            <option value="">{{ 'All' | t }}</option>
            <option value="true">{{ 'Active only' | t }}</option>
            <option value="false">{{ 'Inactive only' | t }}</option>
          </select>
        </div>

        <app-api-error-banner [error]="error()" />

        <app-data-table
          [loading]="loading()"
          [empty]="!loading() && !error() && codes().length === 0"
          [emptyMessage]="'No invitation codes. Create one to let an intake register.' | t"
        >
          <thead>
            <tr>
              <th>{{ 'Code' | t }}</th>
              <th>{{ 'Role' | t }}</th>
              <th>{{ 'Used' | t }}</th>
              <th>{{ 'Remaining' | t }}</th>
              <th>{{ 'Expires' | t }}</th>
              <th>{{ 'Notes' | t }}</th>
              <th>{{ 'Status' | t }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (code of codes(); track code.code) {
              <tr>
                <td class="mono">{{ code.code }}</td>
                <td><span class="badge badge-neutral">{{ code.role }}</span></td>
                <td>{{ code.timesUsed }} / {{ code.maxUses }}</td>
                <td [class.text-faint]="remaining(code) === 0">{{ remaining(code) }}</td>
                <td class="text-muted">{{ code.expiresAt ? (code.expiresAt | localDate) : ('never' | t) }}</td>
                <td class="text-muted">{{ code.notes ?? '—' }}</td>
                <td>
                  <span class="badge" [class.badge-ok]="code.active" [class.badge-neutral]="!code.active">
                    {{ code.active ? ('active' | t) : ('inactive' | t) }}
                  </span>
                </td>
                <td class="row">
                  <button
                    type="button"
                    class="btn btn-sm"
                    [disabled]="busyCode() === code.code"
                    (click)="toggle(code)"
                  >
                    {{ code.active ? ('Deactivate' | t) : ('Activate' | t) }}
                  </button>
                  <button
                    type="button"
                    class="btn btn-sm btn-danger"
                    [disabled]="busyCode() === code.code"
                    (click)="remove(code)"
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

    <app-modal #formModal [title]="'New invitation code' | t" (closed)="formError.set(null)">
      <app-api-error-banner [error]="formError()" />
      <form [formGroup]="form" (ngSubmit)="save()" class="stack">
        <p class="hint" style="margin:0">
          {{ 'The code is generated when you save — unique, and made of characters that survive being read out loud. You cannot choose it: a chosen one is a guess away from the next.' | t }}
        </p>

        <div class="field">
          <label for="ic-role">{{ 'Role granted' | t }}</label>
          <select id="ic-role" formControlName="role">
            @for (role of roles; track role) {
              <option [value]="role">{{ role }}</option>
            }
          </select>
        </div>

        <div class="field" [class.invalid]="invalid('maxUses')">
          <label for="ic-max">{{ 'Maximum uses' | t }}</label>
          <input id="ic-max" type="number" formControlName="maxUses" min="1" max="10000" />
          @if (invalid('maxUses')) {
            <span class="error">{{ 'At least 1.' | t }}</span>
          }
        </div>

        <div class="field">
          <label for="ic-expires">{{ 'Expires' | t }}</label>
          <input id="ic-expires" type="datetime-local" formControlName="expiresAt" />
          <span class="hint">{{ 'Optional. Leave empty and it never expires on its own.' | t }}</span>
        </div>

        <div class="field">
          <label for="ic-notes">{{ 'Notes' | t }}</label>
          <input id="ic-notes" type="text" formControlName="notes" [placeholder]="'Intake 2026-2, Ingeniería' | t" />
          <span class="hint">{{ 'Optional. Which intake, which cohort — for whoever reads this later.' | t }}</span>
        </div>

        <div class="row">
          <button type="submit" class="btn btn-primary" [disabled]="submitting()">
            {{ submitting() ? ('Creating…' | t) : ('Create code' | t) }}
          </button>
          <button type="button" class="btn" (click)="formModal.close()">{{ 'Cancel' | t }}</button>
        </div>
      </form>
    </app-modal>
  `,
})
export class InvitationCodesPage {
  private readonly service = inject(InvitationCodesService);

  readonly roles = INVITATION_ROLES;

  /**
   * How many accounts a code can still create.
   *
   * <p>Derived here because the server does not send it. The model used to declare
   * `remainingUses` as a number, so the template read it, TypeScript agreed it existed, and the
   * column rendered empty on every row since the screen was written.
   */
  remaining(code: InvitationCode): number {
    return remainingUses(code);
  }

  readonly loading = signal(true);
  readonly error = signal<ApiError | null>(null);
  readonly codes = signal<InvitationCode[]>([]);
  readonly busyCode = signal<string | null>(null);

  /** The code the server just minted, shown once so it can be read out or copied. */
  readonly justMinted = signal<string | null>(null);

  readonly submitting = signal(false);
  readonly formError = signal<ApiError | null>(null);

  @ViewChild('formModal') private formModal?: ModalComponent;

  private activeFilter?: boolean;

  form = this.blankForm();

  constructor() {
    this.fetch();
  }

  private blankForm() {
    return new FormGroup({
      role: new FormControl<InvitationRole>('ROLE_STUDENT', { nonNullable: true, validators: [Validators.required] }),
      maxUses: new FormControl(50, {
        nonNullable: true,
        validators: [Validators.required, Validators.min(1), Validators.max(10000)],
      }),
      expiresAt: new FormControl('', { nonNullable: true }),
      notes: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(200)] }),
    });
  }

  invalid(name: 'maxUses'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  private fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.list(this.activeFilter).subscribe({
      next: (codes) => {
        this.codes.set(codes);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }

  onFilter(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.activeFilter = value === '' ? undefined : value === 'true';
    this.fetch();
  }

  openCreate(): void {
    this.form = this.blankForm();
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
    const raw = this.form.getRawValue();

    this.service
      .create({
        role: raw.role,
        maxUses: raw.maxUses,
        // The input gives a local datetime with no zone; the contract wants an instant.
        expiresAt: raw.expiresAt ? new Date(raw.expiresAt).toISOString() : null,
        notes: raw.notes.trim() || null,
      })
      .subscribe({
        next: (created) => {
          this.submitting.set(false);
          this.formModal?.close();
          this.justMinted.set(created.code);
          this.fetch();
        },
        error: (err: unknown) => {
          this.submitting.set(false);
          this.formError.set(err instanceof AppHttpError ? err.apiError : null);
        },
      });
  }

  toggle(code: InvitationCode): void {
    this.busyCode.set(code.code);
    this.error.set(null);
    this.service.setActive(code.code, !code.active).subscribe({
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

  remove(code: InvitationCode): void {
    // Names what is being deleted and what it costs, because the two are different actions:
    // deactivating keeps the history of who used it, deleting does not.
    const used = code.timesUsed > 0 ? ' ' + t('It has been used {times} time(s).', { times: code.timesUsed }) : '';
    if (!window.confirm(t('Delete invitation code {code} ({role})?', { code: code.code, role: code.role }) + used + ' ' + t('Deactivating keeps its history; deleting does not.'))) {
      return;
    }
    this.busyCode.set(code.code);
    this.error.set(null);
    this.service.delete(code.code).subscribe({
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

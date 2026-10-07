import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { ChangeDetectionStrategy, Component, ElementRef, ViewChild, computed, effect, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import type { PageResponse } from '../../../core/http/page-response.model';
import { TokenStore } from '../../../core/auth/token.store';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { DataTableComponent } from '../../../shared/ui/data-table/data-table.component';
import { JsonViewComponent } from '../../../shared/ui/json-view/json-view.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { RoleBadgeComponent } from '../../../shared/ui/role-badge/role-badge.component';
import { DIRECTORY_ROLES, type DirectoryEntry, type DirectoryRole } from './user.model';
import { UsersService } from './users.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { locale, t } from '../../../core/i18n/i18n.service';
import { formatDate } from '@angular/common';
import { LocalDatePipe } from '../../../core/i18n/local-date.pipe';
import { CopyButtonComponent } from '../../../shared/ui/copy-button/copy-button.component';
import { ACCOUNT_ROLES, AccountsService, programOf, type AccountRole, type TemporaryPassword } from './accounts.service';

const PAGE_SIZE = 20;

/**
 * Directory browse + the one admin write this contract exposes on another account: flipping
 * `active`. Names come from Microsoft at every sign-in and the picture is the owner's, through
 * /api/users/me, which is why there is no "edit" action here. Nothing academic is shown: an
 * administrator manages accounts, not records.
 */
@Component({
  selector: 'app-users-page',
  imports: [TranslatePipe, LocalDatePipe, ReactiveFormsModule, CopyButtonComponent, DataTableComponent, RoleBadgeComponent, ApiErrorBannerComponent, ModalComponent, JsonViewComponent, PageIntroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        [title]="'Users' | t"
        [what]="'Everyone with a KApp account.' | t"
        [can]="[('Create an account, with a temporary password to hand over' | t), ('Give someone who forgot theirs a new temporary password' | t), ('Search by name or e-mail' | t), ('Filter by role and status' | t), ('Deactivate or reactivate an account' | t)]"
        [note]="'A temporary password works for 7 days and signs nobody in by itself: the first time, the person chooses their own. Deactivating takes access away, and can be undone.' | t"
      />

      @if (issued(); as given) {
        <div class="card issued" role="status">
          <p class="issued-head">{{ given.created ? ('Account created for {name}' | t: { name: given.name }) : ('New temporary password for {name}' | t: { name: given.name }) }}</p>
          <p class="text-muted issued-email mono">{{ given.password.email }}</p>
          <p class="issued-code mono">{{ given.password.temporaryPassword }}</p>
          <p class="text-muted issued-when">
            {{ 'Temporary: it works until {date}, and the first time it asks for a password of their own.' | t: { date: (given.password.expiresAt | localDate) } }}
          </p>
          <div class="row spread">
            <app-copy-button [text]="message()" [primary]="true" [label]="'Copy the message for {name}' | t: { name: given.name }" />
            <app-copy-button [text]="given.password.temporaryPassword" [label]="'Copy only the password' | t" />
            <button type="button" class="btn btn-sm" (click)="issued.set(null)">{{ 'Dismiss' | t }}</button>
          </div>
          <p class="issued-message">{{ message() }}</p>
          <p class="text-faint issued-once">{{ 'It is shown only now: the server keeps it only as a hash.' | t }}</p>
        </div>
      }

      <div class="card stack">
        <div class="work-bar">
          <div class="field" style="flex: 1 1 16rem; margin-bottom: 0">
            <label for="q">{{ 'Search' | t }}</label>
            <input id="q" type="text" [placeholder]="'name or e-mail' | t" (input)="onQueryInput($event)" />
          </div>
          <div class="field" style="margin-bottom: 0">
            <label for="role">{{ 'Role' | t }}</label>
            <select id="role" (change)="onRoleChange($event)">
              <option value="">{{ 'All roles' | t }}</option>
              @for (role of roles; track role) {
                <option [value]="role">{{ role }}</option>
              }
            </select>
          </div>
          <div class="field" style="margin-bottom: 0">
            <label for="active">{{ 'Status' | t }}</label>
            <select id="active" (change)="onActiveChange($event)">
              <option value="">{{ 'All' | t }}</option>
              <option value="true">{{ 'Active' | t }}</option>
              <option value="false">{{ 'Deactivated' | t }}</option>
            </select>
          </div>
          <button type="button" class="btn btn-primary work-create" (click)="openCreate()">{{ 'Create an account' | t }}</button>
        </div>

        <app-api-error-banner [error]="error()" />

        <app-data-table
          [loading]="loading()"
          [empty]="!loading() && !error() && (result()?.content?.length ?? 0) === 0"
          [emptyMessage]="'No users match these filters.' | t"
          [totalItems]="result()?.totalElements ?? null"
          [page]="page()"
          [totalPages]="result()?.totalPages ?? 0"
          (pageChange)="onPageChange($event)"
        >
          <thead>
            <tr>
              <th>{{ 'Name' | t }}</th>
              <th>{{ 'E-mail' | t }}</th>
              <th>{{ 'Roles' | t }}</th>
              <th>{{ 'Status' | t }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (user of result()?.content ?? []; track user.id) {
              <tr>
                <td>{{ user.firstName }} {{ user.lastName }}</td>
                <td class="mono">{{ user.email }}</td>
                <td>
                  <span class="row">
                    @for (role of user.roles; track role) {
                      <app-role-badge [role]="role" />
                    }
                  </span>
                </td>
                <td>
                  <span class="badge" [class]="user.active ? 'badge-success' : 'badge-neutral'">
                    {{ user.active ? ('active' | t) : ('deactivated' | t) }}
                  </span>
                </td>
                <td class="row">
                  <button type="button" class="btn btn-sm" (click)="view(user)">{{ 'View' | t }}</button>
                  <button
                    type="button"
                    class="btn btn-sm"
                    [class.btn-danger]="user.active"
                    [disabled]="updatingId() === user.id || isSelf(user)"
                    [title]="isSelf(user) ? ('This is your own account. Deactivating it would sign you out and there is no way back in from here.' | t) : ''"
                    (click)="toggleActive(user)"
                  >
                    {{ user.active ? ('Deactivate' | t) : ('Activate' | t) }}
                  </button>
                  @if (!isSelf(user)) {
                    <button type="button" class="btn btn-sm" [disabled]="updatingId() === user.id" (click)="newTemporaryPassword(user)">
                      {{ 'Temporary password' | t }}
                    </button>
                    @if (!user.roles.includes('ROLE_ADMIN')) {
                      <button type="button" class="btn btn-sm btn-danger" [disabled]="updatingId() === user.id" (click)="deleteAccount(user)">
                        {{ 'Delete' | t }}
                      </button>
                    }
                  } @else {
                    <span class="text-muted" style="font-size: 0.75rem">{{ 'you' | t }}</span>
                  }
                </td>
              </tr>
            }
          </tbody>
        </app-data-table>
      </div>
    </div>

    <app-modal #detailModal [title]="'User profile' | t">
      @if (selectedUser(); as user) {
        <div class="stack">
          <p><strong>{{ user.firstName }} {{ user.lastName }}</strong> · {{ user.email }}</p>
          <app-json-view [value]="user" />
        </div>
      }
    </app-modal>

    <app-modal #createModal [title]="'Create an account' | t" (closed)="createError.set(null)">
      <app-api-error-banner [error]="createError()" />
      <form [formGroup]="createForm" (ngSubmit)="createAccount()" class="stack">
        <p class="hint" style="margin:0">
          {{ 'You get a temporary password to hand over. The first time the person signs in, they choose their own.' | t }}
        </p>
        <div class="row spread">
          <div class="field" style="flex: 1 1 10rem" [class.invalid]="invalid('firstName')">
            <label for="acc-first">{{ 'First names' | t }}</label>
            <input id="acc-first" type="text" formControlName="firstName" autocomplete="off" />
          </div>
          <div class="field" style="flex: 1 1 10rem" [class.invalid]="invalid('lastName')">
            <label for="acc-last">{{ 'Last names' | t }}</label>
            <input id="acc-last" type="text" formControlName="lastName" autocomplete="off" />
          </div>
        </div>
        <div class="field" [class.invalid]="invalid('email')">
          <label for="acc-email">{{ 'E-mail' | t }}</label>
          <input id="acc-email" type="email" formControlName="email" autocomplete="off" />
          @if (invalid('email')) {
            <span class="error">{{ 'Write an e-mail address, such as name@konradlorenz.edu.co.' | t }}</span>
          }
        </div>
        <div class="field">
          <label for="acc-role">{{ 'Role' | t }}</label>
          <select id="acc-role" formControlName="role">
            @for (role of accountRoles; track role) {
              <option [value]="role">{{ roleLabel(role) }}</option>
            }
          </select>
        </div>
        @if (createForm.controls.role.value === 'ROLE_STUDENT') {
          <div class="field" [class.invalid]="invalid('studentCode')">
            <label for="acc-code">{{ 'Student code' | t }}</label>
            <input id="acc-code" type="text" inputmode="numeric" formControlName="studentCode" autocomplete="off" />
            @if (invalid('studentCode')) {
              <span class="error">{{ '6 to 20 digits.' | t }}</span>
            } @else if (program(); as code) {
              <span class="hint">{{ 'Program {code}, from the first three digits.' | t: { code } }}</span>
            }
          </div>
        }
        <div class="row">
          <button type="submit" class="btn btn-primary" [disabled]="creating()">
            {{ creating() ? ('Creating…' | t) : ('Create the account' | t) }}
          </button>
          <button type="button" class="btn" (click)="createModal.close()">{{ 'Cancel' | t }}</button>
        </div>
      </form>
    </app-modal>
  `,
  styles: `
    .issued {
      border-color: var(--primary-brand);
    }
    .issued-head {
      margin: 0;
      font-weight: 600;
    }
    .issued-email {
      margin: 0.1rem 0 0;
    }
    .issued-code {
      margin: 0.35rem 0;
      font-size: 1.5rem;
      font-weight: 700;
      letter-spacing: 0.08em;
      user-select: all;
    }
    .issued-when {
      margin: 0 0 0.75rem;
    }
    .issued-once {
      margin: 0.5rem 0 0;
      font-size: 0.8125rem;
    }
    .issued-message {
      margin: 0.75rem 0 0;
      padding: 0.6rem 0.75rem;
      border-radius: 0.5rem;
      background: var(--bg-inset);
      font-size: 0.875rem;
      user-select: all;
    }
  `,
})
export class UsersPage {
  private readonly usersService = inject(UsersService);
  private readonly tokens = inject(TokenStore);

  readonly roles = DIRECTORY_ROLES;

  readonly page = signal(0);
  readonly role = signal<DirectoryRole | ''>('');
  readonly active = signal<'' | 'true' | 'false'>('');
  readonly q = signal('');

  readonly loading = signal(false);
  readonly error = signal<ApiError | null>(null);
  readonly result = signal<PageResponse<DirectoryEntry> | null>(null);

  readonly selectedUser = signal<DirectoryEntry | null>(null);
  readonly updatingId = signal<string | null>(null);

  @ViewChild('detailModal') private detailModal?: ModalComponent;
  @ViewChild('createModal') private createModal?: ModalComponent;

  private readonly accounts = inject(AccountsService);
  readonly accountRoles = ACCOUNT_ROLES;
  readonly creating = signal(false);
  readonly createError = signal<ApiError | null>(null);
  /** The temporary password just issued, shown once: the server keeps only its hash. */
  readonly issued = signal<{ password: TemporaryPassword; name: string; created: boolean } | null>(null);
  createForm = this.blankAccount();
  /** Bumped to list the users again, after one is created. */
  private readonly refresh = signal(0);

  /** What to send the person, ready to paste into an e-mail or a chat. */
  readonly message = computed(() => {
    const given = this.issued();
    if (!given) return '';
    return t('Hi {name}: your KApp account is ready. Sign in with {email} and the temporary password {password}; the first time, it asks you to choose your own. It works until {date}.', {
      name: given.name,
      email: given.password.email,
      password: given.password.temporaryPassword,
      date: formatDate(given.password.expiresAt, 'dd MMM y, HH:mm', locale()),
    });
  });

  private debounceHandle?: ReturnType<typeof setTimeout>;

  private readonly fetchEffect = effect(() => {
    this.refresh();
    const filters = {
      page: this.page(),
      size: PAGE_SIZE,
      role: this.role() || undefined,
      active: this.active() === '' ? undefined : this.active() === 'true',
      q: this.q() || undefined,
    };
    this.loading.set(true);
    this.error.set(null);

    this.usersService.list(filters).subscribe({
      next: (page) => {
        this.result.set(page);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  });

  onQueryInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    clearTimeout(this.debounceHandle);
    this.debounceHandle = setTimeout(() => {
      this.page.set(0);
      this.q.set(value);
    }, 300);
  }

  onRoleChange(event: Event): void {
    this.page.set(0);
    this.role.set((event.target as HTMLSelectElement).value as DirectoryRole | '');
  }

  onActiveChange(event: Event): void {
    this.page.set(0);
    this.active.set((event.target as HTMLSelectElement).value as '' | 'true' | 'false');
  }

  onPageChange(page: number): void {
    this.page.set(page);
  }

  roleLabel(role: AccountRole): string {
    return role === 'ROLE_PROFESSOR' ? t('professor') : t('student');
  }

  private blankAccount() {
    return new FormGroup({
      firstName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(50)] }),
      lastName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(50)] }),
      email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email, Validators.maxLength(100)] }),
      role: new FormControl<AccountRole>('ROLE_PROFESSOR', { nonNullable: true }),
      studentCode: new FormControl('', { nonNullable: true, validators: [Validators.pattern(/^\d{6,20}$/)] }),
    });
  }

  /** The program the student code belongs to, shown under it: the server reads it the same way. */
  program(): string {
    return programOf(this.createForm.controls.studentCode.value);
  }

  invalid(name: 'firstName' | 'lastName' | 'email' | 'studentCode'): boolean {
    const control = this.createForm.controls[name];
    return control.invalid && control.touched;
  }

  openCreate(): void {
    this.createForm = this.blankAccount();
    this.createError.set(null);
    this.createModal?.open();
  }

  /**
   * The account and its profile, at once, with a temporary password to hand over. A student
   * needs its two codes, which the server checks too; a professor's are not sent.
   */
  createAccount(): void {
    const raw = this.createForm.getRawValue();
    const student = raw.role === 'ROLE_STUDENT';
    if (student && !raw.studentCode.trim()) {
      this.createForm.controls.studentCode.setErrors({ required: true });
    }
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.creating.set(true);
    this.createError.set(null);
    const name = `${raw.firstName.trim()} ${raw.lastName.trim()}`;
    this.accounts
      .create({
        email: raw.email.trim(),
        firstName: raw.firstName.trim(),
        lastName: raw.lastName.trim(),
        role: raw.role,
        ...(student ? { studentCode: raw.studentCode.trim() } : {}),
      })
      .subscribe({
        next: (password) => {
          this.creating.set(false);
          this.createModal?.close();
          this.issued.set({ password, name, created: true });
          this.refresh.update((n) => n + 1);
        },
        error: (err: unknown) => {
          this.creating.set(false);
          this.createError.set(err instanceof AppHttpError ? err.apiError : null);
        },
      });
  }

  /** The account and its profile, for good: the person can no longer sign in, and leaves the list. */
  deleteAccount(user: DirectoryEntry): void {
    if (!window.confirm(t('Delete the account of {email} for good? Their profile goes with it, and it cannot be undone. Deactivating keeps it.', { email: user.email }))) {
      return;
    }
    this.updatingId.set(user.id);
    this.accounts.delete(user.id).subscribe({
      next: () => {
        this.updatingId.set(null);
        this.refresh.update((n) => n + 1);
      },
      error: (err: unknown) => {
        this.updatingId.set(null);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }

  /** For somebody who forgot their password: the one they had stops working at once. */
  newTemporaryPassword(user: DirectoryEntry): void {
    if (!window.confirm(t('Give {email} a new temporary password? The one they have stops working at once.', { email: user.email }))) {
      return;
    }
    this.updatingId.set(user.id);
    this.accounts.issueTemporaryPassword(user.id).subscribe({
      next: (password) => {
        this.updatingId.set(null);
        this.issued.set({ password, name: `${user.firstName} ${user.lastName}`, created: false });
      },
      error: (err: unknown) => {
        this.updatingId.set(null);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }

  view(user: DirectoryEntry): void {
    this.selectedUser.set(user);
    this.detailModal?.open();
  }

  /**
   * Your own row, which you may not switch off.
   *
   * <p>Deactivating an account genuinely removes access now, and an account created here
   * signs in with a password that is not the administrator's. Turning your own off signs you out of
   * a portal you cannot let yourself back into; the way back is editing MongoDB by hand. One
   * disabled button is cheaper than that.
   *
   * <p>This is not the whole problem: all four accounts are administrators, so any of them can
   * still switch off the other three. That one needs a decision about what an administrator may
   * do to another administrator, and it is recorded as S14 in SECURITY-AUDIT.md rather than
   * guessed at here.
   */
  isSelf(user: DirectoryEntry): boolean {
    return user.id === this.tokens.decoded()?.claims.sub;
  }

  toggleActive(user: DirectoryEntry): void {
    const nextActive = !user.active;
    const question = nextActive ? t('Reactivate {email}?', { email: user.email }) : t('Deactivate {email}?', { email: user.email });
    if (!window.confirm(question)) {
      return;
    }

    this.updatingId.set(user.id);
    this.usersService.setStatus(user.id, nextActive).subscribe({
      next: (updated) => {
        this.updatingId.set(null);
        this.result.update((current) =>
          current ? { ...current, content: current.content.map((u) => (u.id === updated.id ? updated : u)) } : current,
        );
      },
      error: (err: unknown) => {
        this.updatingId.set(null);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }
}

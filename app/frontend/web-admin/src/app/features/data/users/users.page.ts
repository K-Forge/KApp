import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { ChangeDetectionStrategy, Component, ElementRef, ViewChild, computed, effect, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ALL_ROLES, type Role } from '../../../core/auth/auth.model';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import type { PageResponse } from '../../../core/http/page-response.model';
import { TokenStore } from '../../../core/auth/token.store';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { DataTableComponent } from '../../../shared/ui/data-table/data-table.component';
import { JsonViewComponent } from '../../../shared/ui/json-view/json-view.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { RoleBadgeComponent } from '../../../shared/ui/role-badge/role-badge.component';
import type { UserProfile } from './user.model';
import { UsersService } from './users.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { locale, t } from '../../../core/i18n/i18n.service';
import { formatDate } from '@angular/common';
import { LocalDatePipe } from '../../../core/i18n/local-date.pipe';
import { CopyButtonComponent } from '../../../shared/ui/copy-button/copy-button.component';
import { INVITATION_ROLES, type InvitationRole } from '../invitation-codes/invitation-code.model';
import { InvitationCodesService } from '../invitation-codes/invitation-codes.service';

const PAGE_SIZE = 20;

/**
 * Directory browse + the one admin write this contract exposes on another account: flipping
 * `active`. Everything else about a profile (name, phone, academic record) is only editable by
 * the account owner through /api/users/me, which is why there is no "edit" action here.
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
        [can]="[('Invite someone: a code only they can use, for the role you choose' | t), ('Search by name or e-mail' | t), ('Filter by role and status' | t), ('Deactivate or reactivate an account' | t)]"
        [note]="'The person invited creates the account in the app, with their own password. Deactivating takes access away, and can be undone.' | t"
      />

      @if (invited(); as invite) {
        <div class="card invited" role="status">
          <p class="invited-head">{{ 'Invitation for {name}' | t: { name: invite.name } }}</p>
          <p class="invited-code mono">{{ invite.code }}</p>
          <p class="text-muted invited-when">
            {{ 'Works once, as {role}, until {date}.' | t: { role: roleLabel(invite.role), date: (invite.expiresAt | localDate) } }}
          </p>
          <div class="row spread">
            <app-copy-button [text]="message()" [primary]="true" [label]="'Copy the message for {name}' | t: { name: invite.name }" />
            <app-copy-button [text]="invite.code" [label]="'Copy only the code' | t" />
            <button type="button" class="btn btn-sm" (click)="invited.set(null)">{{ 'Dismiss' | t }}</button>
          </div>
          <p class="invited-message">{{ message() }}</p>
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
          <button type="button" class="btn btn-primary work-create" (click)="openInvite()">{{ 'Invite someone' | t }}</button>
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
              <th>{{ 'Role' | t }}</th>
              <th>{{ 'Status' | t }}</th>
              <th>{{ 'Academic' | t }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (user of result()?.content ?? []; track user.id) {
              <tr>
                <td>{{ user.firstName }} {{ user.lastName }}</td>
                <td class="mono">{{ user.email }}</td>
                <td><app-role-badge [role]="user.role" /></td>
                <td>
                  <span class="badge" [class]="user.active ? 'badge-success' : 'badge-neutral'">
                    {{ user.active ? ('active' | t) : ('deactivated' | t) }}
                  </span>
                </td>
                <td class="text-muted">
                  {{ user.academic ? ('level {level} · {program}' | t: { level: user.academic.currentLevel, program: user.academic.programCode }) : '—' }}
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
                  @if (isSelf(user)) {
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

    <app-modal #inviteModal [title]="'Invite someone' | t" (closed)="inviteError.set(null)">
      <app-api-error-banner [error]="inviteError()" />
      <form [formGroup]="inviteForm" (ngSubmit)="sendInvite()" class="stack">
        <p class="hint" style="margin:0">
          {{ 'You get a code that creates one account, with the role you choose here. Give it to the person: they create the account in the app, with their own password.' | t }}
        </p>
        <div class="field" [class.invalid]="invalid('name')">
          <label for="inv-name">{{ 'Name' | t }}</label>
          <input id="inv-name" type="text" formControlName="name" autocomplete="off" />
          @if (invalid('name')) {
            <span class="error">{{ 'Write the name of the person.' | t }}</span>
          }
        </div>
        <div class="field" [class.invalid]="invalid('email')">
          <label for="inv-email">{{ 'E-mail' | t }}</label>
          <input id="inv-email" type="email" formControlName="email" autocomplete="off" />
          @if (invalid('email')) {
            <span class="error">{{ 'Write an e-mail address, such as name@konradlorenz.edu.co.' | t }}</span>
          }
        </div>
        <div class="field">
          <label for="inv-role">{{ 'Role' | t }}</label>
          <select id="inv-role" formControlName="role">
            @for (role of inviteRoles; track role) {
              <option [value]="role">{{ roleLabel(role) }}</option>
            }
          </select>
        </div>
        <div class="field" [class.invalid]="invalid('days')">
          <label for="inv-days">{{ 'Days it works' | t }}</label>
          <input id="inv-days" type="number" formControlName="days" min="1" max="90" />
          @if (invalid('days')) {
            <span class="error">{{ 'Between 1 and 90 days.' | t }}</span>
          }
        </div>
        <div class="row">
          <button type="submit" class="btn btn-primary" [disabled]="inviting()">
            {{ inviting() ? ('Creating…' | t) : ('Create the invitation' | t) }}
          </button>
          <button type="button" class="btn" (click)="inviteModal.close()">{{ 'Cancel' | t }}</button>
        </div>
      </form>
    </app-modal>
  `,
  styles: `
    .invited {
      border-color: var(--primary-brand);
    }
    .invited-head {
      margin: 0;
      font-weight: 600;
    }
    .invited-code {
      margin: 0.25rem 0;
      font-size: 1.5rem;
      letter-spacing: 0.08em;
      user-select: all;
    }
    .invited-when {
      margin: 0 0 0.75rem;
    }
    .invited-message {
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

  readonly roles = ALL_ROLES;

  readonly page = signal(0);
  readonly role = signal<Role | ''>('');
  readonly active = signal<'' | 'true' | 'false'>('');
  readonly q = signal('');

  readonly loading = signal(false);
  readonly error = signal<ApiError | null>(null);
  readonly result = signal<PageResponse<UserProfile> | null>(null);

  readonly selectedUser = signal<UserProfile | null>(null);
  readonly updatingId = signal<string | null>(null);

  @ViewChild('detailModal') private detailModal?: ModalComponent;
  @ViewChild('inviteModal') private inviteModal?: ModalComponent;

  private readonly invitations = inject(InvitationCodesService);
  readonly inviteRoles = INVITATION_ROLES;
  readonly inviting = signal(false);
  readonly inviteError = signal<ApiError | null>(null);
  /** The invitation just made, shown once so its code can be handed over. */
  readonly invited = signal<{ code: string; name: string; email: string; role: InvitationRole; expiresAt: string } | null>(null);
  inviteForm = this.blankInvite();

  /** What to send the person, ready to paste into an e-mail or a chat. */
  readonly message = computed(() => {
    const invite = this.invited();
    if (!invite) return '';
    return t('Hi {name}: to create your KApp account as {role}, use the invitation code {code} when you sign up in the app. It works once, until {date}.', {
      name: invite.name,
      role: this.roleLabel(invite.role),
      code: invite.code,
      date: formatDate(invite.expiresAt, 'dd MMM y, HH:mm', locale()),
    });
  });

  private debounceHandle?: ReturnType<typeof setTimeout>;

  private readonly fetchEffect = effect(() => {
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
    this.role.set((event.target as HTMLSelectElement).value as Role | '');
  }

  onActiveChange(event: Event): void {
    this.page.set(0);
    this.active.set((event.target as HTMLSelectElement).value as '' | 'true' | 'false');
  }

  onPageChange(page: number): void {
    this.page.set(page);
  }

  roleLabel(role: InvitationRole): string {
    return role === 'ROLE_PROFESSOR' ? t('professor') : t('student');
  }

  private blankInvite() {
    return new FormGroup({
      name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(80)] }),
      email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email, Validators.maxLength(100)] }),
      role: new FormControl<InvitationRole>('ROLE_STUDENT', { nonNullable: true }),
      days: new FormControl(14, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(90)] }),
    });
  }

  invalid(name: 'name' | 'email' | 'days'): boolean {
    const control = this.inviteForm.controls[name];
    return control.invalid && control.touched;
  }

  openInvite(): void {
    this.inviteForm = this.blankInvite();
    this.inviteError.set(null);
    this.inviteModal?.open();
  }

  /**
   * An invitation is a code that creates one account: the auth service mints it, with the person
   * written in its notes, so the codes list says who each one was for. Making the account here,
   * password and all, would need endpoints neither service has yet.
   */
  sendInvite(): void {
    if (this.inviteForm.invalid) {
      this.inviteForm.markAllAsTouched();
      return;
    }
    const raw = this.inviteForm.getRawValue();
    const name = raw.name.trim();
    const email = raw.email.trim();
    const expiresAt = new Date(Date.now() + raw.days * 24 * 60 * 60 * 1000).toISOString();
    this.inviting.set(true);
    this.inviteError.set(null);
    this.invitations
      .create({ role: raw.role, maxUses: 1, expiresAt, notes: t('For {name} <{email}>', { name, email }).slice(0, 200) })
      .subscribe({
        next: (created) => {
          this.inviting.set(false);
          this.inviteModal?.close();
          this.invited.set({ code: created.code, name, email, role: created.role, expiresAt: created.expiresAt ?? expiresAt });
        },
        error: (err: unknown) => {
          this.inviting.set(false);
          this.inviteError.set(err instanceof AppHttpError ? err.apiError : null);
        },
      });
  }

  view(user: UserProfile): void {
    this.selectedUser.set(user);
    this.detailModal?.open();
  }

  /**
   * Your own row, which you may not switch off.
   *
   * <p>Deactivating an account genuinely removes access now, and accounts are born from
   * registration - an invitation from here is a code, not an account. Turning your own off signs you out of
   * a portal you cannot let yourself back into; the way back is editing MongoDB by hand. One
   * disabled button is cheaper than that.
   *
   * <p>This is not the whole problem: all four accounts are administrators, so any of them can
   * still switch off the other three. That one needs a decision about what an administrator may
   * do to another administrator, and it is recorded as S14 in SECURITY-AUDIT.md rather than
   * guessed at here.
   */
  isSelf(user: UserProfile): boolean {
    return user.id === this.tokens.decoded()?.claims.sub;
  }

  toggleActive(user: UserProfile): void {
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

import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { ChangeDetectionStrategy, Component, ElementRef, ViewChild, effect, inject, signal } from '@angular/core';
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
import { t } from '../../../core/i18n/i18n.service';

const PAGE_SIZE = 20;

/**
 * Directory browse + the one admin write this contract exposes on another account: flipping
 * `active`. Everything else about a profile (name, phone, academic record) is only editable by
 * the account owner through /api/users/me, which is why there is no "edit" action here.
 */
@Component({
  selector: 'app-users-page',
  imports: [TranslatePipe, DataTableComponent, RoleBadgeComponent, ApiErrorBannerComponent, ModalComponent, JsonViewComponent, PageIntroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        [title]="'Users' | t"
        [what]="'Everyone with a KApp account, and the only place to see who they are and switch them on or off.' | t"
        [can]="[('Search by name or e-mail, accent-insensitively' | t), ('Filter by role and by status' | t), ('Open a profile' | t), ('Deactivate or reactivate an account' | t)]"
        [note]="'There is deliberately no create or delete here. Accounts are born from registration — a person signs up with an invitation code, which is what decides their role — so creating one here would invent a user that never agreed to anything. Deactivating is the reversible way to take access away.' | t"
      />

      <div class="card stack">
        <div class="row spread">
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

  view(user: UserProfile): void {
    this.selectedUser.set(user);
    this.detailModal?.open();
  }

  /**
   * Your own row, which you may not switch off.
   *
   * <p>Deactivating an account genuinely removes access now, and there is no create-user path
   * here by design - accounts are born from registration. Turning your own off signs you out of
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

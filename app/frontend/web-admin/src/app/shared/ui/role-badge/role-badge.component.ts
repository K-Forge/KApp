import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { roleLabel } from '../../../core/auth/auth.model';
import { t } from '../../../core/i18n/i18n.service';

/** Small badge for a single role, colored consistently everywhere a role is shown. */
@Component({
  selector: 'app-role-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [class]="badgeClass()">{{ label() }}</span>`,
})
export class RoleBadgeComponent {
  readonly role = input.required<string>();

  /** The role's name, except an account the directory does not classify: that one reads "No role". */
  label(): string {
    return this.role() === 'ROLE_UNASSIGNED' ? t('No role') : roleLabel(this.role());
  }

  badgeClass(): string {
    switch (this.role()) {
      case 'ROLE_ADMIN':
        return 'badge-danger';
      case 'ROLE_PROFESSOR':
        return 'badge-primary';
      case 'ROLE_STUDENT':
        return 'badge-success';
      case 'ROLE_UNASSIGNED':
        return 'badge-warning';
      default:
        return 'badge-neutral';
    }
  }
}

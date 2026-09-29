import { PageIntroComponent } from '../../shared/ui/page-intro/page-intro.component';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ALL_ROLES, type Role } from '../../core/auth/auth.model';
import { TokenStore } from '../../core/auth/token.store';
import { OpenApiCatalogService } from '../../core/openapi/openapi-catalog.service';
import type { ConsoleOperation } from '../../core/openapi/console-operation.model';
import { resolveRoleRequirement, type RoleRequirement } from '../../core/openapi/role-requirement';
import { DataTableComponent } from '../../shared/ui/data-table/data-table.component';
import { RoleBadgeComponent } from '../../shared/ui/role-badge/role-badge.component';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

interface InspectedRow {
  op: ConsoleOperation;
  requirement: RoleRequirement;
  reachable: 'yes' | 'no' | 'unknown';
}

/**
 * Answers "what can this role actually reach" by combining the caller's own token with a role
 * requirement parsed from every operation across the specs (see role-requirement.ts for how,
 * and its honest limits). Meant for checking the authorization matrix by hand, not as a substitute
 * for the server's own enforcement - every requirement here is read from documentation prose, not
 * verified against a live response.
 */
@Component({
  selector: 'app-role-inspector-page',
  imports: [TranslatePipe, DataTableComponent, RoleBadgeComponent, PageIntroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        [title]="'Who can do what' | t"
        [what]="'Every endpoint across the services, and which roles the API contract says may call it.' | t"
        [can]="[('Filter by service, by role, or by path' | t), ('See which operations are public' | t), ('Spot the ones whose contract says nothing usable' | t)]"
        [note]="'Read from each operation’s x-roles, written from the authorization the services actually enforce — their @PreAuthorize annotations and the map’s security configuration. A row marked “inferred” was guessed from the description’s prose instead, and should be treated as one. Either way, the thing that would fail if a rule and its contract ever disagreed is the backend’s authorization matrix tests; the API console is how you check a single row by hand.' | t"
      />

      <div class="card stack">
        <div>
          <span class="text-muted">{{ 'Your token carries:' | t }}</span>
          <div class="row spread" style="margin-top: 0.4rem">
            @for (role of myRoles(); track role) {
              <app-role-badge [role]="role" />
            } @empty {
              <span class="text-faint">{{ 'no token loaded' | t }}</span>
            }
          </div>
        </div>

        <div class="field" style="max-width: 20rem; margin-bottom: 0">
          <label for="inspect-role">{{ 'Inspect as role' | t }}</label>
          <select id="inspect-role" (change)="onRoleChange($event)">
            @for (role of roles; track role) {
              <option [value]="role" [selected]="role === inspectedRole()">{{ role }}</option>
            }
          </select>
        </div>

        <p class="text-muted" style="margin: 0">
          {{ '{value} of {value2} endpoints are documented as reachable by {inspectedRole}. {value3} don’t state their required role in a form this page can parse - check those by hand.' | t: { value: summary().reachable, value2: summary().total, inspectedRole: inspectedRole(), value3: summary().unknown } }}
        </p>
      </div>

      <div class="card">
        <div class="field" style="max-width: 20rem">
          <label for="filter">{{ 'Filter by path' | t }}</label>
          <input id="filter" type="text" [placeholder]="'e.g. buildings' | t" (input)="onFilterInput($event)" />
        </div>

        <app-data-table [empty]="filteredRows().length === 0" [emptyMessage]="'No endpoints match this filter.' | t">
          <thead>
            <tr>
              <th>{{ 'Service' | t }}</th>
              <th>{{ 'Method' | t }}</th>
              <th>{{ 'Path' | t }}</th>
              <th>{{ 'Required role(s)' | t }}</th>
              <th>{{ 'Reachable' | t }}</th>
            </tr>
          </thead>
          <tbody>
            @for (row of filteredRows(); track row.op.serviceId + row.op.method + row.op.path) {
              <tr>
                <td>{{ serviceLabels[row.op.serviceId] ?? row.op.serviceId | t }}</td>
                <td><span class="badge badge-neutral">{{ row.op.method.toUpperCase() }}</span></td>
                <td class="mono">{{ row.op.path }}</td>
                <td>
                  @if (row.requirement.kind === 'public') {
                    <span class="badge badge-success">{{ 'public' | t }}</span>
                  } @else if (row.requirement.kind === 'service-only') {
                    <span class="badge badge-neutral" [title]="'Reachable only from inside the compose network, with the shared internal token. It has no gateway route at all.' | t">
                      {{ 'service only' | t }}
                    </span>
                  } @else if (row.requirement.kind === 'roles') {
                    <div class="row spread">
                      @for (role of row.requirement.roles; track role) {
                        <app-role-badge [role]="role" />
                      }
                    </div>
                  } @else {
                    <span class="badge badge-warning">{{ 'not documented' | t }}</span>
                  }
                  @if (row.requirement.source === 'inferred') {
                    <span
                      class="badge badge-warning"
                      [title]="'Read from the description’s prose, not declared in x-roles. Treat it as a guess and check it in the API console.' | t"
                    >{{ 'inferred' | t }}</span>
                  }
                </td>
                <td>
                  @switch (row.reachable) {
                    @case ('yes') {
                      <span class="badge badge-success">{{ 'yes' | t }}</span>
                    }
                    @case ('no') {
                      <span class="badge badge-danger">{{ 'no' | t }}</span>
                    }
                    @default {
                      <span class="badge badge-warning">{{ 'check manually' | t }}</span>
                    }
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
export class RoleInspectorPage {
  private readonly tokenStore = inject(TokenStore);
  private readonly catalog = inject(OpenApiCatalogService);

  readonly roles = ALL_ROLES;
  /** Each service by the name the API console gives it. */
  readonly serviceLabels: Record<string, string> = Object.fromEntries(this.catalog.services.map((s) => [s.id, s.label]));
  readonly myRoles = this.tokenStore.roles;

  readonly inspectedRole = signal<Role>((this.tokenStore.roles()[0] as Role | undefined) ?? 'ROLE_STUDENT');
  readonly filterText = signal('');

  private readonly allRows = computed<InspectedRow[]>(() => {
    const role = this.inspectedRole();
    return this.catalog.allOperations().map((op) => {
      const requirement = resolveRoleRequirement(op);
      const reachable: InspectedRow['reachable'] =
        requirement.kind === 'public' || (requirement.kind === 'roles' && requirement.roles.includes(role))
          ? 'yes'
          : requirement.kind === 'unknown'
            ? 'unknown'
            : 'no';
      return { op, requirement, reachable };
    });
  });

  readonly filteredRows = computed(() => {
    const filter = this.filterText().trim().toLowerCase();
    const rows = this.allRows();
    if (!filter) {
      return rows;
    }
    return rows.filter((row) => row.op.path.toLowerCase().includes(filter) || row.op.summary.toLowerCase().includes(filter));
  });

  readonly summary = computed(() => {
    const rows = this.allRows();
    return {
      total: rows.length,
      reachable: rows.filter((r) => r.reachable === 'yes').length,
      unknown: rows.filter((r) => r.reachable === 'unknown').length,
    };
  });

  onRoleChange(event: Event): void {
    this.inspectedRole.set((event.target as HTMLSelectElement).value as Role);
  }

  onFilterInput(event: Event): void {
    this.filterText.set((event.target as HTMLInputElement).value);
  }
}

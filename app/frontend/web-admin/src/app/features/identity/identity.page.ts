import { PageIntroComponent } from '../../shared/ui/page-intro/page-intro.component';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TokenStore } from '../../core/auth/token.store';
import { ClockService } from '../../core/clock/clock.service';
import { JsonViewComponent } from '../../shared/ui/json-view/json-view.component';
import { RoleBadgeComponent } from '../../shared/ui/role-badge/role-badge.component';
import { TokenCountdownComponent } from '../../shared/ui/token-countdown/token-countdown.component';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { t, locale } from '../../core/i18n/i18n.service';

/**
 * "Decode and display the JWT" - the reason this whole app exists. A mobile developer signing in
 * as a role they don't normally use needs to see, without guessing, exactly what the token says:
 * who it is, what it grants, and how long it has left.
 */
@Component({
  selector: 'app-identity-page',
  imports: [TranslatePipe, JsonViewComponent, TokenCountdownComponent, RoleBadgeComponent, PageIntroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        [title]="'My token' | t"
        [what]="'What your access token says: who you are, your roles and how long it lasts.' | t"
        [can]="[('Read what it says' | t), ('Copy the token for a script' | t)]"
        [note]="'Decoded here, not verified: only the services check its signature.' | t"
      />

      @if (decoded(); as decoded) {
        <div class="card stack">
          <div class="row-between">
            <h2 style="margin:0">{{ 'Access token' | t }}</h2>
            <app-token-countdown />
          </div>

          @if (isExpired()) {
            <div class="badge badge-danger" role="alert" style="align-self: start">
              {{ 'This token expired at {expiryLocal}. Sign in again to get a fresh one.' | t: { expiryLocal: expiryLocal() } }}
            </div>
          }

          <dl class="claims-grid">
            <dt>{{ 'Subject (sub)' | t }}</dt>
            <dd class="mono">{{ decoded.claims.sub }}</dd>

            <dt>{{ 'E-mail' | t }}</dt>
            <dd>{{ decoded.claims.email }}</dd>

            <dt>{{ 'Roles' | t }}</dt>
            <dd>
              <div class="row spread">
                @for (role of roles(); track role) {
                  <app-role-badge [role]="role" />
                } @empty {
                  <span class="text-faint">{{ 'none' | t }}</span>
                }
              </div>
            </dd>

            <dt>{{ 'Issuer (iss)' | t }}</dt>
            <dd class="mono">{{ decoded.claims.iss }}</dd>

            <dt>{{ 'Issued at (iat)' | t }}</dt>
            <dd>{{ issuedLocal() }}</dd>

            <dt>{{ 'Expires at (exp)' | t }}</dt>
            <dd>{{ expiryLocal() }}</dd>

            <dt>{{ 'Header' | t }}</dt>
            <dd class="mono">{{ 'alg={value} kid={value2}' | t: { value: decoded.header['alg'], value2: decoded.header['kid'] } }}</dd>
          </dl>
        </div>

        <div class="card stack">
          <h2 style="margin:0">{{ 'Raw claims' | t }}</h2>
          <app-json-view [value]="decoded.claims" />
        </div>

        <div class="card stack">
          <h2 style="margin:0">{{ 'The token itself' | t }}</h2>
          <p class="text-muted small" style="margin:0">
            {{ 'What a script such as export-map-snapshot.py asks for. Whoever holds it acts as you until it expires, so paste it only where you would type your password.' | t }}
          </p>
          <app-json-view [value]="raw()" />
        </div>
      } @else {
        <div class="card empty-state">
          <p>{{ 'No token loaded. Sign in to see what a KApp access token contains.' | t }}</p>
        </div>
      }
    </div>
  `,
  styles: `
    .claims-grid {
      display: grid;
      grid-template-columns: 10rem 1fr;
      row-gap: 0.75rem;
      column-gap: 1rem;
      margin: 0;
    }
    .claims-grid dt {
      color: var(--text-muted);
      font-size: 0.8125rem;
      font-weight: 600;
    }
    .claims-grid dd {
      margin: 0;
      /* A subject id, an issuer URL and an institutional e-mail have no spaces to break at;
         without this they ran past the card's edge on a phone. */
      overflow-wrap: anywhere;
      min-width: 0;
    }
    /* Ten rems of label beside the value left a phone with a column narrower than any of the
       values in it. There each claim reads as a label over its value. */
    @media (max-width: 640px) {
      .claims-grid {
        grid-template-columns: 1fr;
        row-gap: 0.2rem;
      }
      .claims-grid dd + dt {
        margin-top: 0.6rem;
      }
    }
  `,
})
export class IdentityPage {
  private readonly tokenStore = inject(TokenStore);
  private readonly clock = inject(ClockService);

  readonly decoded = this.tokenStore.decoded;
  readonly raw = this.tokenStore.raw;
  readonly roles = this.tokenStore.roles;

  readonly isExpired = computed(() => {
    this.clock.now();
    return this.tokenStore.isExpired();
  });

  readonly issuedLocal = computed(() => {
    const claims = this.decoded()?.claims;
    return claims ? formatMoment(claims.iat) : '';
  });

  readonly expiryLocal = computed(() => {
    const claims = this.decoded()?.claims;
    return claims ? formatMoment(claims.exp) : '';
  });
}

/**
 * One date format for the whole portal: "11 Sep 2026, 00:51".
 *
 * <p>`toLocaleString()` gives the browser's format and the DatePipe's 'short' gives Angular's
 * default locale, so the same instant read three different ways on three screens - and
 * "9/11/26" is 9 November to half this team and 11 September to the other half. A written-out
 * month cannot be misread, and a 24-hour clock is what everyone here uses anyway.
 */
function formatMoment(epochSeconds: number): string {
  const d = new Date(epochSeconds * 1000);
  // In the portal's language: "29 sept 2026, 15:42" or "29 Sep 2026, 15:42".
  return new Intl.DateTimeFormat(locale(), {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(d);
}

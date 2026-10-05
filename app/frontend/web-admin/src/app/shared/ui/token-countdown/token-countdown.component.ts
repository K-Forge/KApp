import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ClockService } from '../../../core/clock/clock.service';
import { TokenStore } from '../../../core/auth/token.store';
import { secondsUntilExpiry } from '../../../core/auth/jwt.util';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/** Live "expires in mm:ss" readout, or a clear EXPIRED badge once the deadline passes. */
@Component({
  selector: 'app-token-countdown',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (claims(); as claims) {
      @if (remaining() > 0) {
        <span class="badge" [class.badge-warning]="remaining() < 300" [class.badge-primary]="remaining() >= 300"
              [title]="('The session ends in ' | t) + formatted()">
          <span class="label">{{ 'expires in' | t }} </span>{{ formatted() }}
        </span>
      } @else {
        <span class="badge badge-danger">{{ 'expired' | t }}</span>
      }
    } @else {
      <span class="badge badge-neutral">{{ 'no token' | t }}</span>
    }
  `,
  // On a phone the words go and the time stays: the header has one row, and the sign-out
  // button was the one pushed off it.
  styles: `
    .badge {
      white-space: nowrap;
    }
    @media (max-width: 480px) {
      .label {
        display: none;
      }
    }
  `,
})
export class TokenCountdownComponent {
  private readonly tokenStore = inject(TokenStore);
  private readonly clock = inject(ClockService);

  /** Accepted for API symmetry with other small display components; unused today. */
  readonly compact = input(false);

  readonly claims = computed(() => this.tokenStore.decoded()?.claims ?? null);

  readonly remaining = computed(() => {
    const claims = this.claims();
    if (!claims) {
      return 0;
    }
    this.clock.now(); // re-evaluate every tick
    return Math.max(0, secondsUntilExpiry(claims));
  });

  readonly formatted = computed(() => {
    const total = this.remaining();
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    const seconds = (total % 60).toString().padStart(2, '0');
    return hours > 0 ? `${hours}:${minutes.toString().padStart(2, '0')}:${seconds}` : `${minutes}:${seconds}`;
  });
}

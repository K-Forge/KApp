import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { copyText } from '../copy-text';

/**
 * A button that copies some text, with an icon that says what happened: two sheets to copy, a
 * tick once it has, and a warning when the browser let nothing copy - then it asks to copy by
 * hand rather than looking as if it had worked.
 */
@Component({
  selector: 'app-copy-button',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="btn btn-sm copy"
      [class.btn-primary]="primary()"
      [class.btn-ghost]="ghost()"
      [class.done]="state() === 'copied'"
      (click)="copy()"
    >
      <svg class="icon" viewBox="0 0 24 24" width="15" height="15" aria-hidden="true">
        @switch (state()) {
          @case ('copied') {
            <path class="tick" d="M20 6 9 17l-5-5" />
          }
          @case ('failed') {
            <circle cx="12" cy="12" r="9.5" />
            <path d="M12 7.5v5.5M12 16.5h.01" />
          }
          @default {
            <rect x="8.5" y="8.5" width="12.5" height="12.5" rx="2.5" />
            <path d="M15.5 8.5V5.5A2.5 2.5 0 0 0 13 3H5.5A2.5 2.5 0 0 0 3 5.5V13a2.5 2.5 0 0 0 2.5 2.5h3" />
          }
        }
      </svg>
      <span aria-live="polite">
        {{ state() === 'copied' ? ('Copied' | t) : state() === 'failed' ? ('Select it and copy it by hand' | t) : label() || ('Copy' | t) }}
      </span>
    </button>
  `,
  styles: `
    .copy {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
    }
    .icon {
      flex: none;
      fill: none;
      stroke: currentColor;
      stroke-width: 2;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
    .done .icon {
      animation: pop 260ms ease-out;
    }
    .tick {
      stroke-width: 2.6;
    }
    @keyframes pop {
      0% { transform: scale(0.6); }
      60% { transform: scale(1.15); }
      100% { transform: scale(1); }
    }
  `,
})
export class CopyButtonComponent {
  /** What it copies. */
  readonly text = input.required<string>();
  /** What the button says before it is pressed; "Copy" when not given. */
  readonly label = input<string>('');
  readonly primary = input(false);
  readonly ghost = input(false);
  readonly state = signal<'idle' | 'copied' | 'failed'>('idle');

  async copy(): Promise<void> {
    const copied = await copyText(this.text());
    this.state.set(copied ? 'copied' : 'failed');
    setTimeout(() => this.state.set('idle'), copied ? 1500 : 4000);
  }
}

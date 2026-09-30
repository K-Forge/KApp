import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/** Pretty-printed, monospace, copyable JSON - used for tokens, request bodies and responses alike. */
@Component({
  selector: 'app-json-view',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="json-view">
      <button type="button" class="btn btn-ghost btn-sm copy-btn" (click)="copy()">
        {{ copyState() === 'copied' ? ('Copied' | t) : copyState() === 'failed' ? ('Select it and copy it by hand' | t) : ('Copy' | t) }}
      </button>
      <pre class="mono">{{ pretty() }}</pre>
    </div>
  `,
  styles: `
    .json-view {
      position: relative;
      background: var(--bg-inset);
      border: 1px solid var(--border);
      border-radius: var(--radius-md);
    }
    pre {
      margin: 0;
      padding: 0.75rem;
      /* Room for the copy button above the text, so a long token never runs under it. */
      padding-top: 2.4rem;
      overflow: auto;
      max-height: 28rem;
      font-size: 0.75rem;
      white-space: pre-wrap;
      word-break: break-word;
    }
    .copy-btn {
      position: absolute;
      top: 0.4rem;
      right: 0.4rem;
    }
  `,
})
export class JsonViewComponent {
  readonly value = input<unknown>(undefined);
  readonly copyState = signal<'idle' | 'copied' | 'failed'>('idle');

  readonly pretty = computed(() => {
    const value = this.value();
    if (value === undefined) {
      return '';
    }
    try {
      return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
    } catch {
      return String(value);
    }
  });

  /**
   * Copies what is shown. The Clipboard API exists only on a secure page - HTTPS or localhost - so
   * on the portal opened over plain http by a tailnet address the button did nothing at all. There,
   * and wherever the browser refuses, it copies the old way, still inside the tap; when both fail it
   * says so instead of looking as if it worked.
   */
  async copy(): Promise<void> {
    const text = this.pretty();
    let copied = false;
    if (window.isSecureContext && navigator.clipboard) {
      try {
        await navigator.clipboard.writeText(text);
        copied = true;
      } catch {
        copied = copyByHand(text);
      }
    } else {
      copied = copyByHand(text);
    }
    this.copyState.set(copied ? 'copied' : 'failed');
    setTimeout(() => this.copyState.set('idle'), copied ? 1500 : 4000);
  }
}

/** Copies through a hidden text box and the browser's copy command, as pages did before the Clipboard API. */
function copyByHand(text: string): boolean {
  const box = document.createElement('textarea');
  box.value = text;
  box.setAttribute('readonly', '');
  box.style.position = 'fixed';
  box.style.top = '0';
  box.style.opacity = '0';
  document.body.appendChild(box);
  box.select();
  box.setSelectionRange(0, text.length); // Safari on iOS selects nothing without it
  let done = false;
  try {
    done = typeof document.execCommand === 'function' && document.execCommand('copy');
  } catch {
    done = false;
  }
  box.remove();
  return done;
}

import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { copyText } from '../copy-text';

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

  /** Copies what is shown; when the browser lets nothing copy, the button says so instead of looking as if it worked. */
  async copy(): Promise<void> {
    const copied = await copyText(this.pretty());
    this.copyState.set(copied ? 'copied' : 'failed');
    setTimeout(() => this.copyState.set('idle'), copied ? 1500 : 4000);
  }
}

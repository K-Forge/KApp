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
        {{ copied() ? ('Copied' | t) : ('Copy' | t) }}
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
  readonly copied = signal(false);

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

  async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.pretty());
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    } catch {
      // Clipboard access can be denied by the browser; the value is still fully visible above.
    }
  }
}

import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CopyButtonComponent } from '../copy-button/copy-button.component';

/** Pretty-printed, monospace, copyable JSON - used for tokens, request bodies and responses alike. */
@Component({
  selector: 'app-json-view',
  imports: [CopyButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="json-view">
      <app-copy-button class="copy-btn" [text]="pretty()" [ghost]="true" />
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
}

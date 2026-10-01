import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/**
 * The heading of a screen, plus a straight answer to "what is this for and what can I do here".
 *
 * <p>Every page carried a title and, at best, a sentence of prose buried under it. Somebody
 * opening this portal for the first time — which is every teammate, once — had to infer the
 * purpose of eleven screens from their nouns. This makes that explicit and puts it in one
 * component so the shape cannot drift from screen to screen.
 *
 * <p>All of it but the title sits behind "What can I do here?", closed: open on every page it
 * pushed the work below the first screen. The survey's "How to measure" folds the same way.
 *
 * @param what  one sentence: what this screen is for. Not a description of the UI
 * @param can   what you can actually do here, one line each. Empty when a screen is read-only,
 *              which is itself worth stating rather than leaving somebody hunting for a button
 * @param note  the thing that would otherwise surprise them — a rule the server enforces, a
 *              capability that is deliberately absent, a word that means something specific here
 */
@Component({
  selector: 'app-page-intro',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-intro">
      <div class="page-intro-head">
        <h1>{{ title() }}</h1>
        <ng-content select="[actions]" />
      </div>
      <!-- Closed until somebody wants it: open, it pushed every page's work below the first screen. -->
      <details class="help">
        <summary>
          <svg class="help-icon" viewBox="0 0 16 16" width="15" height="15" aria-hidden="true">
            <circle cx="8" cy="8" r="6.8" fill="none" stroke="currentColor" stroke-width="1.4" />
            <path d="M8 7.2v4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            <circle cx="8" cy="4.9" r="0.95" fill="currentColor" />
          </svg>
          {{ 'What can I do here?' | t }}
          <svg class="help-chevron" viewBox="0 0 12 12" width="11" height="11" aria-hidden="true">
            <path d="M2.5 4.5 6 8l3.5-3.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </summary>
        <div class="help-body">
          <p class="help-what">{{ what() }}</p>
          @if (can().length) {
            <ul class="help-can">
              @for (item of can(); track item) {
                <li>{{ item }}</li>
              }
            </ul>
          }
          @if (note()) {
            <p class="help-note">{{ note() }}</p>
          }
        </div>
      </details>
    </div>
  `,
  styles: `
    .page-intro {
      margin-bottom: 1rem;
    }
    .page-intro-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .page-intro-head h1 {
      margin: 0;
    }
  `,
})
export class PageIntroComponent {
  readonly title = input.required<string>();
  readonly what = input.required<string>();
  readonly can = input<string[]>([]);
  readonly note = input<string>('');
}

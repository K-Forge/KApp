import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { navEntryFor } from '../../../layout/nav';

/**
 * The heading of a screen, plus a straight answer to "what is this for and what can I do here".
 *
 * <p>Every page carried a title and, at best, a sentence of prose buried under it. Somebody
 * opening this portal for the first time — which is every teammate, once — had to infer the
 * purpose of eleven screens from their nouns. This makes that explicit and puts it in one
 * component so the shape cannot drift from screen to screen.
 *
 * <p>All of it but the title sits behind "What can I do here?", closed: open on every page it
 * pushed the work below the first screen. A page with more to explain - the survey's "How to
 * measure" - puts its own folded help beside it, in the same row, as `[help]`.
 *
 * <p>The title carries the sidebar's icon for its section and the group it sits in, so the page
 * says where you are the way the menu does, and the top of the screen is not one lonely word.
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
        @if (entry; as e) {
          <span class="page-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24"><path [attr.d]="e.link.icon" /></svg>
          </span>
        }
        <div class="page-title">
          @if (entry; as e) {
            <span class="page-group">{{ e.group.title | t }}</span>
          }
          <h1>{{ title() }}</h1>
          <ng-content select="[lede]" />
        </div>
        <div class="page-actions">
          <ng-content select="[actions]" />
        </div>
      </div>
      <div class="help-row">
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
      <ng-content select="[help]" />
      </div>
    </div>
  `,
  styles: `
    .page-intro {
      margin-bottom: 1rem;
    }
    .page-intro-head {
      display: flex;
      align-items: center;
      gap: 0.5rem 0.9rem;
      flex-wrap: wrap;
    }
    /* The sidebar's glyph, larger, on a tile of the selected section's colour. */
    .page-icon {
      flex: none;
      display: grid;
      place-items: center;
      width: 3rem;
      height: 3rem;
      border-radius: 0.9rem;
      background: var(--nav-active-bg);
      color: var(--nav-active-text);
      box-shadow: 0 1px 0 color-mix(in srgb, var(--bg-elevated) 70%, transparent) inset, var(--shadow-sm);
    }
    .page-icon svg {
      width: 1.5rem;
      height: 1.5rem;
      fill: none;
      stroke: currentColor;
      stroke-width: 1.75;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
    .page-title {
      flex: 1 1 14rem;
      min-width: 0;
    }
    .page-group {
      display: block;
      font-size: 0.6875rem;
      font-weight: 700;
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--text-muted);
    }
    .page-title h1 {
      margin: 0;
      line-height: 1.15;
    }
    .page-actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.4rem;
    }
    .page-actions:empty {
      display: none;
    }
    /* The help a page folds away, side by side: open, one grows under its own pill and the others
       wait beside it, at the same height. */
    .help-row {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      gap: 0.5rem;
      margin-top: 0.6rem;
    }
  `,
})
export class PageIntroComponent {
  /** The sidebar entry this page lives under, for its icon and its group; none for a page off the menu. */
  protected readonly entry = navEntryFor(inject(Router).url);

  readonly title = input.required<string>();
  readonly what = input.required<string>();
  readonly can = input<string[]>([]);
  readonly note = input<string>('');
}

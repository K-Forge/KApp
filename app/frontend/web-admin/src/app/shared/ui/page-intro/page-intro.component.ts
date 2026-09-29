import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/**
 * The heading of a screen, plus a straight answer to "what is this for and what can I do here".
 *
 * <p>Every page carried a title and, at best, a sentence of prose buried under it. Somebody
 * opening this portal for the first time — which is every teammate, once — had to infer the
 * purpose of eleven screens from their nouns. This makes that explicit and puts it in one
 * component so the shape cannot drift from screen to screen.
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

      <p class="page-intro-what">{{ what() }}</p>

      @if (can().length || note()) {
        <button
          type="button"
          class="page-intro-toggle"
          (click)="expanded.set(!expanded())"
          [attr.aria-expanded]="expanded()"
        >
          {{ expanded() ? ('Hide what you can do here' | t) : ('What you can do here' | t) }}
          <svg viewBox="0 0 12 12" width="12" height="12" aria-hidden="true">
            <path d="M2.5 4.5 6 8l3.5-3.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
        <div class="page-intro-extra" [class.expanded]="expanded()">
          @if (can().length) {
            <ul class="page-intro-can">
              @for (item of can(); track item) {
                <li>{{ item }}</li>
              }
            </ul>
          }

          @if (note()) {
            <p class="page-intro-note">{{ note() }}</p>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .page-intro {
      margin-bottom: 1.25rem;
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
    /*
     * Body colour, not muted: this paragraph sits directly on the page ground, and the ground
     * is lit. Over the centre of the dark theme's teal it measured 2.78:1 muted - the contrast
     * sweep cannot see it, because the sweep reads text against --bg, which is the ground
     * before the lights are composited on top. The hierarchy against the title survives on size
     * and weight, which is where it was actually coming from.
     */
    .page-intro-what {
      margin: 0.5rem 0 0;
      max-width: 62ch;
      color: var(--text);
    }
    .page-intro-can {
      margin: 0.6rem 0 0;
      padding: 0;
      list-style: none;
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem 1.25rem;
      max-width: 78ch;
    }
    .page-intro-can li {
      position: relative;
      padding-left: 1.1rem;
      font-size: 0.8125rem;
      /* On the lit ground too - see .page-intro-what. */
      color: var(--text);
    }
    /* A tick rather than a bullet: these are things you can do, not things to read. */
    .page-intro-can li::before {
      content: '';
      position: absolute;
      left: 0;
      top: 0.45em;
      width: 0.5rem;
      height: 0.28rem;
      border-left: 2px solid var(--brand-teal);
      border-bottom: 2px solid var(--brand-teal);
      transform: rotate(-45deg);
    }
    /* Carries the institutional green on its edge - the one place a page states the thing
       that would otherwise be discovered by being surprised. */
    .page-intro-note {
      margin: 0.75rem 0 0;
      padding: 0.55rem 0.8rem;
      max-width: 74ch;
      font-size: 0.8125rem;
      border-left: 3px solid var(--brand-green);
      background: var(--bg-inset);
      border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
    }

    .page-intro-toggle {
      display: none;
    }
    /*
     * On a phone the list and the note fold away behind one line. Open, the intro filled the
     * whole first screen of every page, so what the page was for came before any of the page.
     * The sentence saying what it is for stays; the rest is a tap away.
     */
    @media (max-width: 640px) {
      .page-intro-toggle {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        margin-top: 0.5rem;
        padding: 0.35rem 0;
        border: 0;
        background: none;
        /* Body colour: it sits on the lit ground, where the teal of a link measured 2.4:1 over
           the centre of the pink light. */
        color: var(--text);
        font-size: 0.8125rem;
        font-weight: 600;
      }
      .page-intro-toggle[aria-expanded='true'] svg {
        transform: rotate(180deg);
      }
      .page-intro-extra:not(.expanded) {
        display: none;
      }
      .page-intro-can {
        margin-top: 0.25rem;
      }
    }
  `,
})
export class PageIntroComponent {
  readonly title = input.required<string>();
  readonly what = input.required<string>();
  readonly can = input<string[]>([]);
  readonly note = input<string>('');

  /** Whether a phone shows the list and the note. Wider screens always do. */
  readonly expanded = signal(false);
}

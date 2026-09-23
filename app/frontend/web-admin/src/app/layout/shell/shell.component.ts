import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { TokenStore } from '../../core/auth/token.store';
import { ApiConfigService } from '../../core/config/api-config.service';
import { ThemeService } from '../../core/theme/theme.service';
import { TokenCountdownComponent } from '../../shared/ui/token-countdown/token-countdown.component';

interface NavLink {
  path: string;
  label: string;
  /** Inline SVG path data, 24x24. Emoji render differently on every platform and read as
      decoration; a stroked glyph reads as an icon and inherits the current text colour. */
  icon: string;
}

interface NavGroup {
  title: string;
  links: NavLink[];
}

// Grouped because eleven flat entries is a list to read, not a menu to use. The three groups
// are the three reasons somebody opens this portal: to check who they are, to look after the
// data, or to inspect how the API behaves.
const NAV_GROUPS: NavGroup[] = [
  {
    title: 'Academic',
    links: [
      { path: '/data/programs', label: 'Programs', icon: 'M3 7l9-4 9 4-9 4-9-4zm0 5l9 4 9-4M3 17l9 4 9-4' },
      { path: '/data/pensums', label: 'Pensums', icon: 'M4 5a2 2 0 012-2h12a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2zM8 7h8M8 11h8M8 15h5' },
    ],
  },
  {
    title: 'Campus',
    links: [
      { path: '/data/buildings', label: 'Buildings', icon: 'M3 21h18M5 21V5a2 2 0 012-2h6a2 2 0 012 2v16M9 7h2M9 11h2M9 15h2M15 21v-8h4v8' },
      { path: '/data/spaces', label: 'Spaces', icon: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z' },
      { path: '/data/space-types', label: 'Space types', icon: 'M7 7h.01M3 11l8-8h8a2 2 0 012 2v8l-8 8a2 2 0 01-2.83 0l-5.17-5.17a2 2 0 010-2.83z' },
    ],
  },
  {
    title: 'People and access',
    links: [
      { path: '/data/users', label: 'Users', icon: 'M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM22 21v-2a4 4 0 00-3-3.87' },
      { path: '/data/invitation-codes', label: 'Invitation codes', icon: 'M15 7a4 4 0 11-5.66 5.66L3 19v2h2l6.34-6.34A4 4 0 0115 7zM16 8h.01' },
      { path: '/data/visitor-passes', label: 'Visitor passes', icon: 'M3 7a2 2 0 012-2h14a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2zM3 11h18M7 15h4' },
    ],
  },
  {
    title: 'Inspect',
    links: [
      { path: '/my-token', label: 'My token', icon: 'M12 2l8 4v6c0 5-3.4 8.6-8 10-4.6-1.4-8-5-8-10V6l8-4zM9 12l2 2 4-4' },
      { path: '/who-can-do-what', label: 'Who can do what', icon: 'M9 11l3 3L22 4M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11' },
      { path: '/api-console', label: 'API console', icon: 'M8 9l-4 3 4 3M16 9l4 3-4 3M13 5l-2 14' },
    ],
  },
];

/** Nav + header shared by every authenticated screen. Login stays outside so it renders alone. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TokenCountdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="shell">
      <!--
        First stop in the tab order, invisible until it is focused. Without it a keyboard user
        walks the whole sidebar - fourteen stops - before reaching the page's own first control,
        on every single page.

        The click is handled rather than left to the href. A bare fragment href goes through the
        router, which resolved it to the empty route and redirected to /my-token - a skip link
        that changes the page is worse than no skip link. The href stays so it still reads as a
        link and works if scripting is off.
      -->
      <a class="skip-link" href="#shell-content" (click)="skipToContent($event)">Skip to content</a>

      <header class="shell-header">
        <!-- The API console, not the token screen: the console is where somebody spends the
             session, and the token is one click away from it anyway. -->
        <a class="brand" routerLink="/api-console">
          <img src="/konrad-logo.png" alt="Fundación Universitaria Konrad Lorenz" width="34" height="34" />
          <span class="brand-text">
            <strong>KApp</strong>
            <span class="brand-sub">Admin Portal</span>
          </span>
        </a>

        <div class="header-actions">
          <app-token-countdown />

          <button
            type="button"
            class="icon-btn"
            (click)="editBaseUrl()"
            [title]="'Gateway: ' + baseUrl() + ' — click to change'"
            aria-label="Change the gateway address"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 2a10 10 0 100 20 10 10 0 000-20zM2 12h20M12 2a15 15 0 010 20 15 15 0 010-20" />
            </svg>
            <span class="icon-btn-label">{{ shortBaseUrl() }}</span>
          </button>

          <button
            type="button"
            class="icon-btn"
            (click)="cycleTheme()"
            [title]="'Theme: ' + theme.preference() + ' — click to change'"
            [attr.aria-label]="'Theme: ' + theme.preference()"
          >
            @if (theme.preference() === 'dark') {
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z" /></svg>
            } @else if (theme.preference() === 'light') {
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="12" cy="12" r="4" />
                <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
              </svg>
            } @else {
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="2" y="4" width="20" height="14" rx="2" />
                <path d="M8 21h8M12 18v3" />
              </svg>
            }
            <span class="icon-btn-label">{{ theme.preference() }}</span>
          </button>

          <button type="button" class="icon-btn danger" (click)="logout()" title="Sign out">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9" />
            </svg>
            <span class="icon-btn-label">Sign out</span>
          </button>
        </div>
      </header>

      <div class="shell-body">
        <nav class="shell-nav" aria-label="Sections">
          @for (group of groups; track group.title) {
            <p class="nav-group-title">{{ group.title }}</p>
            @for (link of group.links; track link.path) {
              <a
                [routerLink]="link.path"
                routerLinkActive="active"
                ariaCurrentWhenActive="page"
                class="nav-link"
              >
                <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path [attr.d]="link.icon" />
                </svg>
                {{ link.label }}
              </a>
            }
          }
        </nav>

        <main id="shell-content" class="shell-content" tabindex="-1">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styles: `
    .shell {
      min-height: 100dvh;
      display: flex;
      flex-direction: column;
    }

    /* ── Header ──────────────────────────────────────────────────────────────
       A tinted band rather than another white strip. The three institutional
       hues run along the bottom edge as a hairline, which is where the brand
       belongs in a tool: present, not shouting. */
    /*
     * Off-screen until focused, then pinned over the header. Not display:none - that would
     * take it out of the tab order, which is the one thing it exists for.
     */
    .skip-link {
      /* fixed, not absolute: it must sit over the header wherever the shell is scrolled to,
         and it does not depend on an ancestor happening to be positioned. */
      position: fixed;
      left: 0.5rem;
      top: -3rem;
      z-index: 100;
      padding: 0.5rem 0.85rem;
      background: var(--bg-elevated);
      color: var(--text);
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-sm);
      font-size: 0.8125rem;
      text-decoration: none;
      transition: top var(--transition-fast);
    }
    /* :focus, not :focus-visible. A skip link is only ever reached by keyboard, and
       :focus-visible is a heuristic that does not fire when focus is moved by script - which
       would leave the element focused and invisible, the worst of both. */
    .skip-link:focus {
      top: 0.5rem;
    }

    .shell-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.6rem 1.25rem;
      background: var(--header-bg);
      border-bottom: 1px solid var(--border);
      position: relative;
    }
    .shell-header::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      bottom: -1px;
      height: 2px;
      background: linear-gradient(
        90deg,
        var(--brand-teal) 0%,
        var(--brand-teal) 33%,
        var(--brand-pink) 33%,
        var(--brand-pink) 66%,
        var(--brand-green) 66%
      );
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      text-decoration: none;
      color: inherit;
      border-radius: var(--radius-md);
      padding: 0.15rem 0.35rem;
    }
    /* The mark is a transparent PNG of overlapping shapes. It needs room and no
       container: a rounded tile behind it reads as a button, and at 32px the
       overlaps muddied into noise.

       The src is absolute. Relative, the browser resolves it against the current
       route - so it loaded on /identity and 404'd on /data/programs, showing the
       alt text instead. Every route in this app is nested except the first one
       anybody sees, which is exactly how that survived a look. */
    .brand img {
      flex: 0 0 auto;
      display: block;
    }
    .brand-text {
      display: flex;
      flex-direction: column;
      line-height: 1.1;
    }
    .brand strong {
      font-size: 1.0625rem;
      letter-spacing: -0.01em;
    }
    .brand-sub {
      font-size: 0.75rem;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      flex-wrap: wrap;
    }

    /* An icon with its label beside it: recognisable at a glance, unambiguous
       when read. The label hides on narrow screens; the icon and the title
       attribute carry it from there. */
    .icon-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.35rem 0.6rem;
      font-size: 0.8125rem;
      font-weight: 500;
      color: var(--text-muted);
      background: transparent;
      border: 1px solid transparent;
      border-radius: var(--radius-md);
      cursor: pointer;
      transition: background var(--transition-fast), color var(--transition-fast);
    }
    .icon-btn svg {
      width: 16px;
      height: 16px;
      fill: none;
      stroke: currentColor;
      stroke-width: 1.75;
      stroke-linecap: round;
      stroke-linejoin: round;
      flex: 0 0 auto;
    }
    .icon-btn:hover {
      background: var(--bg-hover);
      color: var(--text);
    }
    .icon-btn.danger:hover {
      background: var(--danger-bg);
      color: var(--danger);
    }
    .icon-btn-label {
      white-space: nowrap;
    }

    .shell-body {
      flex: 1;
      display: flex;
      min-height: 0;
    }

    /* ── Sidebar ─────────────────────────────────────────────────────────── */
    .shell-nav {
      width: 15rem;
      flex: 0 0 auto;
      display: flex;
      flex-direction: column;
      gap: 0.1rem;
      padding: 0.85rem 0.65rem;

      /*
       * Lit by the same lamps as the page.
       *
       * The sidebar sits below the strip under the header, so light coming off that strip
       * should fall on it - and it did not, because this painted an opaque colour straight
       * over the page's background and cut the glow off at its edge.
       *
       * The lights go on TOP of --nav-bg rather than replacing it, so the sidebar keeps
       * reading as chrome. Attaching them to the viewport is what makes it continuous: the
       * gradients are positioned against the viewport wherever they are used, so what shows
       * here is exactly the slice that would have been behind it.
       */
      background-color: var(--nav-bg);
      background-image: var(--page-lights);
      background-attachment: fixed;
      background-repeat: no-repeat;

      border-right: 1px solid var(--border);
      overflow-y: auto;
    }

    .nav-group-title {
      margin: 0.9rem 0 0.3rem 0.6rem;
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.07em;
      color: var(--text-faint);
    }
    .nav-group-title:first-child {
      margin-top: 0.15rem;
    }

    .nav-link {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      padding: 0.5rem 0.6rem;
      border-radius: var(--radius-md);
      /*
       * Body-text colour, not the muted one, because the sidebar is lit now.
       *
       * The contrast sweep cannot see this: it reads text against --nav-bg, which is the
       * colour BEFORE the lights are composited on top. Worked out by hand instead. A muted
       * link over the centre of the pink light lands on about #e091b1 in the light theme -
       * 2.6:1 - and over the teal in the dark theme on #426b68, which is 2.8:1. Both well
       * under. Dimming the lights enough to rescue the muted colour would have meant about
       * 15%, which is not a light, it is a smudge. So the text takes the weight: 6.6:1 and
       * 4.8:1 on those same two backgrounds.
       *
       * Nothing is lost by it. What separated the active item from the rest was never this
       * colour - it is the fill, the edge and the weight.
       */
      color: var(--text);
      text-decoration: none;
      /* Was 0.875rem — a size below the body text, in the one place you read while looking
         somewhere else. */
      font-size: 0.9375rem;
      border-left: 3px solid transparent;
      transition: background var(--transition-fast), color var(--transition-fast);
    }
    .nav-icon {
      width: 18px;
      height: 18px;
      flex: 0 0 auto;
      fill: none;
      stroke: currentColor;
      stroke-width: 1.75;
      stroke-linecap: round;
      stroke-linejoin: round;
      opacity: 0.85;
    }
    .nav-link:hover {
      background: var(--bg-hover);
      color: var(--text);
    }
    /*
     * The active item is FILLED, not tinted.
     *
     * It used to be --primary-bg on --nav-bg: two colours about two percent of lightness
     * apart, which is present in the stylesheet and invisible on the screen. A sidebar whose
     * selected row you have to hunt for is a sidebar that is not doing its one job.
     */
    .nav-link.active {
      background: var(--nav-active-bg);
      color: var(--nav-active-text);
      border-left-color: var(--brand-teal);
      font-weight: 600;
    }
    .nav-link.active:hover {
      background: var(--nav-active-bg);
      color: var(--nav-active-text);
    }
    .nav-link.active .nav-icon {
      opacity: 1;
    }

    .shell-content {
      flex: 1;
      overflow-y: auto;
      padding: 1.5rem;
    }

    @media (max-width: 720px) {
      .shell-body {
        flex-direction: column;
      }
      /* One scrolling strip rather than a wrapped block. Wrapped, eleven links
         took four rows and a third of a phone screen before any content. */
      .shell-nav {
        width: 100%;
        flex-direction: row;
        flex-wrap: nowrap;
        overflow-x: auto;
        gap: 0.25rem;
        padding: 0.5rem;
        border-right: none;
        border-bottom: 1px solid var(--border);
        scrollbar-width: none;
      }
      .shell-nav::-webkit-scrollbar {
        display: none;
      }
      /* Group headings are a vertical device; in a horizontal strip they would
         be eleven more things to scroll past. */
      .nav-group-title {
        display: none;
      }
      .nav-link {
        white-space: nowrap;
        flex: 0 0 auto;
        border-left: none;
        border-bottom: 3px solid transparent;
      }
      .nav-link.active {
        border-left-color: transparent;
        border-bottom-color: var(--brand-teal);
      }
      .shell-content {
        padding: 1rem;
      }
      .shell-header {
        flex-wrap: wrap;
        gap: 0.5rem;
        padding: 0.6rem 1rem;
      }
      /* The labels are the first thing to go; the icons still say what each does. */
      .icon-btn-label {
        display: none;
      }
    }
  `})
export class ShellComponent {

  /**
   * Moves focus into the page body without navigating.
   *
   * <p>`<main>` carries `tabindex="-1"` so it can receive focus programmatically while staying
   * out of the tab order; without that, focus() on a non-interactive element does nothing and
   * the next Tab starts from the top again.
   */
  skipToContent(event: Event): void {
    event.preventDefault();
    document.getElementById('shell-content')?.focus();
  }

  private readonly auth = inject(AuthService);
  private readonly config = inject(ApiConfigService);
  protected readonly tokenStore = inject(TokenStore);
  protected readonly theme = inject(ThemeService);

  readonly groups = NAV_GROUPS;

  /** The host alone. The full URL is in the tooltip; the button only has to say which machine. */
  readonly shortBaseUrl = () => {
    try {
      return new URL(this.baseUrl()).host;
    } catch {
      return this.baseUrl();
    }
  };
  readonly baseUrl = this.config.baseUrl;

  readonly themeIcons: Record<string, string> = { system: '◐', light: '☀', dark: '☽' };
  themeIcon(): string {
    return this.themeIcons[this.theme.preference()] ?? '';
  }

  cycleTheme(): void {
    const order = ['system', 'light', 'dark'] as const;
    const next = order[(order.indexOf(this.theme.preference()) + 1) % order.length];
    this.theme.set(next);
  }

  editBaseUrl(): void {
    const next = window.prompt('Gateway base URL', this.baseUrl());
    if (next) {
      this.config.setBaseUrl(next);
    }
  }

  logout(): void {
    this.auth.logout();
  }
}

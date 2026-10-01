import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { isTokenExpired } from '../../core/auth/jwt.util';
import { TokenStore } from '../../core/auth/token.store';
import { ClockService } from '../../core/clock/clock.service';
import { ApiConfigService } from '../../core/config/api-config.service';
import { ThemeService } from '../../core/theme/theme.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TokenCountdownComponent } from '../../shared/ui/token-countdown/token-countdown.component';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

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
    title: /* i18n */ 'Academic',
    links: [
      { path: '/data/programs', label: /* i18n */ 'Programs', icon: 'M3 7l9-4 9 4-9 4-9-4zm0 5l9 4 9-4M3 17l9 4 9-4' },
      { path: '/data/pensums', label: /* i18n */ 'Pensums', icon: 'M4 5a2 2 0 012-2h12a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2zM8 7h8M8 11h8M8 15h5' },
    ],
  },
  {
    title: /* i18n */ 'Campus',
    links: [
      { path: '/data/campus', label: /* i18n */ 'Campus map', icon: 'M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2zM9 4v14M15 6v14' },
      { path: '/data/survey', label: /* i18n */ 'Survey', icon: 'M3 17 17 3l4 4L7 21zM7 13l2 2M10 10l2 2M13 7l2 2' },
      { path: '/data/buildings', label: /* i18n */ 'Buildings', icon: 'M3 21h18M5 21V5a2 2 0 012-2h6a2 2 0 012 2v16M9 7h2M9 11h2M9 15h2M15 21v-8h4v8' },
      { path: '/data/floors', label: /* i18n */ 'Floor editor', icon: 'M3 3h18v18H3zM3 9h18M9 9v12M15 15h6' },
      { path: '/data/spaces', label: /* i18n */ 'Spaces', icon: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z' },
    ],
  },
  {
    title: /* i18n */ 'People and access',
    links: [
      { path: '/data/users', label: /* i18n */ 'Users', icon: 'M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM22 21v-2a4 4 0 00-3-3.87' },
      { path: '/data/invitation-codes', label: /* i18n */ 'Invitation codes', icon: 'M15 7a4 4 0 11-5.66 5.66L3 19v2h2l6.34-6.34A4 4 0 0115 7zM16 8h.01' },
      { path: '/data/visitor-passes', label: /* i18n */ 'Visitor passes', icon: 'M3 7a2 2 0 012-2h14a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2zM3 11h18M7 15h4' },
    ],
  },
  {
    title: /* i18n */ 'Inspect',
    links: [
      { path: '/my-token', label: /* i18n */ 'My token', icon: 'M12 2l8 4v6c0 5-3.4 8.6-8 10-4.6-1.4-8-5-8-10V6l8-4zM9 12l2 2 4-4' },
      { path: '/who-can-do-what', label: /* i18n */ 'Who can do what', icon: 'M9 11l3 3L22 4M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11' },
      { path: '/api-console', label: /* i18n */ 'API console', icon: 'M8 9l-4 3 4 3M16 9l4 3-4 3M13 5l-2 14' },
    ],
  },
];

/** Nav + header shared by every authenticated screen. Login stays outside so it renders alone. */
@Component({
  selector: 'app-shell',
  host: { '(document:keydown.escape)': 'onEscape()' },
  imports: [TranslatePipe, RouterOutlet, RouterLink, RouterLinkActive, TokenCountdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="shell" [class.nav-hidden]="navHidden()" [class.drawer-open]="drawerOpen()">
      <!--
        First stop in the tab order, invisible until it is focused. Without it a keyboard user
        walks the whole sidebar - fourteen stops - before reaching the page's own first control,
        on every single page.

        The click is handled rather than left to the href. A bare fragment href goes through the
        router, which resolved it to the empty route and redirected to /my-token - a skip link
        that changes the page is worse than no skip link. The href stays so it still reads as a
        link and works if scripting is off.
      -->
      <a class="skip-link" href="#shell-content" (click)="skipToContent($event)">{{ 'Skip to content' | t }}</a>

      <header class="shell-header">
        <div class="header-start">
          <!-- Hides the sidebar on a tablet, where the plan wants the width, and opens it as a
               drawer on a phone, where it has no room to stay open at all. -->
          <button
            type="button"
            class="icon-btn nav-toggle"
            (click)="toggleNav()"
            aria-controls="shell-nav"
            [attr.aria-expanded]="navShown()"
            [title]="navShown() ? ('Hide the menu' | t) : ('Show the menu' | t)"
            [attr.aria-label]="navShown() ? ('Hide the menu' | t) : ('Show the menu' | t)"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
          </button>

          <!-- The API console, not the token screen: the console is where somebody spends the
               session, and the token is one click away from it anyway. -->
          <a class="brand" routerLink="/api-console">
            <img src="/konrad-logo.png" [alt]="'Fundación Universitaria Konrad Lorenz' | t" width="34" height="34" />
            <span class="brand-text">
              <strong>{{ 'KApp' | t }}</strong>
              <span class="brand-sub">{{ 'Admin Portal' | t }}</span>
            </span>
          </a>
        </div>

        <div class="header-actions">
          <app-token-countdown />

          <button
            type="button"
            class="icon-btn"
            (click)="editBaseUrl()"
            [title]="'Gateway: {url} — click to change' | t: { url: baseUrl() }"
            [attr.aria-label]="'Change the gateway address' | t"
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
            [title]="'Theme: {theme} — click to change' | t: { theme: (theme.preference() | t) }"
            [attr.aria-label]="'Theme: {theme}' | t: { theme: (theme.preference() | t) }"
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
            <span class="icon-btn-label">{{ theme.preference() | t }}</span>
          </button>

          <button
            type="button"
            class="icon-btn"
            (click)="cycleLanguage()"
            [title]="'Language: {language} — click to change' | t: { language: languageName() }"
            [attr.aria-label]="'Language: {language}' | t: { language: languageName() }"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 5h9M8.5 3v2M10.5 5c-.7 4.2-3.3 7.6-6.5 9.5M6 9c1.3 2.3 3.2 4 5.5 5M13 21l4-9 4 9M14.3 18h5.4" />
            </svg>
            <span class="icon-btn-label">{{ languageLabel() }}</span>
          </button>

          <button type="button" class="icon-btn danger" (click)="logout()" [title]="'Sign out' | t">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9" />
            </svg>
            <span class="icon-btn-label">{{ 'Sign out' | t }}</span>
          </button>
        </div>
      </header>

      <div class="shell-body">
        @if (drawerOpen()) {
          <div class="nav-backdrop" (click)="closeDrawer()" aria-hidden="true"></div>
        }
        <nav id="shell-nav" class="shell-nav" [attr.aria-label]="'Sections' | t">
          @for (group of groups; track group.title) {
            <p class="nav-group-title">{{ group.title | t }}</p>
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
                {{ link.label | t }}
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
    .shell-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.6rem 1.25rem;
      background: var(--header-bg);
      border-bottom: 1px solid var(--border);
      /* Pinned, so the menu button is within reach from the bottom of a long page too. Sticky
         also positions it, which the strip drawn under it needs. */
      position: sticky;
      top: 0;
      z-index: 40;
    }
    .shell-header::after {
      content: '';
      position: absolute;
      left: 0;
      right: 0;
      bottom: -1px;
      height: 2px;
      background: var(--brand-strip);
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
    .header-start {
      display: flex;
      align-items: center;
      gap: 0.25rem;
      min-width: 0;
    }
    .nav-toggle {
      padding: 0.45rem;
    }
    .icon-btn.nav-toggle svg {
      width: 20px;
      height: 20px;
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

    /* One row, always: wrapping dropped the sign-out button under the rest on a phone. The
       brand gives way instead, since the actions are what the header is for. */
    .header-actions {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      flex-shrink: 0;
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
      border-left-color: var(--nav-active-edge);
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
      min-width: 0;
      overflow-y: auto;
      padding: 1.5rem;
    }

    /*
     * Hidden, the sidebar slides out past the left edge instead of vanishing, and the page
     * takes its width back. On an iPad drawing a floor, that width is the plan's.
     */
    @media (min-width: 721px) {
      .shell.nav-hidden .shell-nav {
        margin-left: -15rem;
        visibility: hidden;
      }
    }
    /* One transition for both: the wide screen slides the margin, the phone the drawer. */
    @media (prefers-reduced-motion: no-preference) {
      .shell-nav {
        transition: margin-left 180ms ease, transform 200ms ease, visibility 200ms;
      }
    }

    /*
     * A phone has no room for a sidebar, and the strip of links that stood in for one ate a row
     * of the screen while showing three of its eleven entries. The menu is a drawer there: out
     * of the way until the button in the header asks for it, and gone again once a section is
     * picked.
     */
    @media (max-width: 720px) {
      .shell-nav {
        position: fixed;
        top: 0;
        bottom: 0;
        left: 0;
        z-index: 60;
        width: min(18rem, 86vw);
        padding: 1rem 0.75rem calc(1rem + env(safe-area-inset-bottom));
        background-color: var(--bg-elevated);
        transform: translateX(-100%);
        visibility: hidden;
      }
      .shell.drawer-open .shell-nav {
        transform: none;
        visibility: visible;
        box-shadow: var(--shadow-md);
      }
      .nav-backdrop {
        position: fixed;
        inset: 0;
        z-index: 55;
        background: var(--scrim);
      }
      .shell-content {
        padding: 1rem;
      }
      .shell-header {
        gap: 0.5rem;
        padding: 0.5rem 0.75rem;
      }
      .header-actions {
        gap: 0.15rem;
      }
      .icon-btn:not(.nav-toggle) {
        padding: 0.4rem;
      }
      /* The labels are the first thing to go; the icons still say what each does. */
      .icon-btn-label,
      .brand-sub {
        display: none;
      }
    }
    /* The mark alone says whose portal this is; the name gives its room to the countdown. */
    @media (max-width: 400px) {
      .brand-text {
        display: none;
      }
    }
  `})
export class ShellComponent {
  private readonly router = inject(Router);

  /** Below this width the sidebar is a drawer, the same breakpoint the styles switch on. */
  private readonly narrowQuery = matchNarrow();
  readonly narrow = signal(this.narrowQuery?.matches ?? false);

  /** On a wide screen: whether the sidebar has been put away. Remembered on this device. */
  readonly navHidden = signal(readNavHidden());

  /** On a phone: whether the drawer is out. Never remembered - it opens only when asked. */
  readonly drawerOpen = signal(false);

  readonly navShown = computed(() => (this.narrow() ? this.drawerOpen() : !this.navHidden()));

  constructor() {
    const destroyRef = inject(DestroyRef);
    const query = this.narrowQuery;
    if (query) {
      const onChange = (event: MediaQueryListEvent) => {
        this.narrow.set(event.matches);
        this.drawerOpen.set(false);
      };
      query.addEventListener('change', onChange);
      destroyRef.onDestroy(() => query.removeEventListener('change', onChange));
    }
    // Picking a section is what the drawer was opened for; leaving it over the page after
    // that would make every visit two taps.
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe(() => this.drawerOpen.set(false));

    // The token's deadline is the session's. The shell is on screen exactly while somebody is
    // signed in, so it watches the clock and signs out the second the token runs out.
    const clock = inject(ClockService);
    effect(() => {
      const claims = this.tokenStore.decoded()?.claims;
      if (claims && isTokenExpired(claims, clock.now())) {
        untracked(() => this.auth.expire());
      }
    });
  }

  toggleNav(): void {
    if (this.narrow()) {
      this.drawerOpen.update((open) => !open);
      return;
    }
    const hidden = !this.navHidden();
    this.navHidden.set(hidden);
    try {
      localStorage.setItem(NAV_HIDDEN_KEY, hidden ? 'true' : 'false');
    } catch {
      // Non-fatal: the sidebar just comes back on the next visit.
    }
  }

  closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  /** Escape puts the drawer away and hands focus back to the button that opened it. */
  onEscape(): void {
    if (this.drawerOpen()) {
      this.drawerOpen.set(false);
      document.querySelector<HTMLElement>('.nav-toggle')?.focus();
    }
  }

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
  private readonly i18n = inject(I18nService);

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

  /** "ES" or "EN", and "auto" while it follows the system's. */
  languageLabel(): string {
    const code = this.i18n.language().toUpperCase();
    return this.i18n.preference() === 'system' ? `${code} · auto` : code;
  }

  languageName(): string {
    const name = this.i18n.language() === 'es' ? 'Español' : 'English';
    return this.i18n.preference() === 'system' ? this.i18n.t('{language}, as the system', { language: name }) : name;
  }

  /** The system's language, then Spanish, then English, as the theme cycles. */
  cycleLanguage(): void {
    const order = ['system', 'es', 'en'] as const;
    this.i18n.set(order[(order.indexOf(this.i18n.preference()) + 1) % order.length]);
  }

  cycleTheme(): void {
    const order = ['system', 'light', 'dark'] as const;
    const next = order[(order.indexOf(this.theme.preference()) + 1) % order.length];
    this.theme.set(next);
  }

  editBaseUrl(): void {
    const next = window.prompt(this.i18n.t('Gateway base URL'), this.baseUrl());
    if (next) {
      this.config.setBaseUrl(next);
    }
  }

  logout(): void {
    this.auth.logout();
  }
}

const NAV_HIDDEN_KEY = 'kapp-admin:nav-hidden';

function readNavHidden(): boolean {
  try {
    return localStorage.getItem(NAV_HIDDEN_KEY) === 'true';
  } catch {
    return false;
  }
}

function matchNarrow(): MediaQueryList | null {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(max-width: 720px)')
    : null;
}

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<ShellComponent>>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
  });

  // Without it a keyboard user walks the whole sidebar before reaching the page's own first
  // control, on every page.
  it('puts the skip link first in the tab order', () => {
    const focusable = fixture.nativeElement.querySelectorAll(
      'a[href], button:not([disabled]), input, select, textarea',
    );
    expect(focusable[0].className).toContain('skip-link');
    expect(focusable[0].textContent.trim()).toBe('Skip to content');
  });

  // main carries tabindex="-1" so it can take focus programmatically while staying out of the
  // tab order; without it, focus() on a non-interactive element does nothing.
  it('gives the skip link somewhere focusable to land', () => {
    const main = fixture.nativeElement.querySelector('main#shell-content');
    expect(main).toBeTruthy();
    expect(main.getAttribute('tabindex')).toBe('-1');
  });

  // A bare fragment href went through the router, which resolved it to the empty route and
  // redirected to /my-token. A skip link that changes the page is worse than none.
  it('moves focus without navigating', () => {
    const link = fixture.nativeElement.querySelector('.skip-link') as HTMLAnchorElement;
    const event = new MouseEvent('click', { bubbles: true, cancelable: true });
    link.dispatchEvent(event);
    fixture.detectChanges();

    expect(event.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('main#shell-content'));
  });

  // The active item was coloured but not announced, so a screen reader could not say which page
  // it was on. routerLinkActive writes aria-current on whichever link matches at runtime - and
  // it only does that because every link carries this binding. Verified live in the browser;
  // this guards the binding from being dropped.
  it('asks the router to mark the active section for assistive technology', () => {
    const links = [...fixture.nativeElement.querySelectorAll('nav a')] as HTMLElement[];
    expect(links.length).toBeGreaterThan(0);
    for (const link of links) {
      expect(link.getAttribute('ariaCurrentWhenActive')).toBe('page');
    }
  });

  // On an iPad the plan wants the sidebar's width. The button puts it away, says so to
  // assistive technology, and brings it back.
  it('puts the sidebar away and brings it back', () => {
    const shell = fixture.nativeElement.querySelector('.shell') as HTMLElement;
    const toggle = fixture.nativeElement.querySelector('.nav-toggle') as HTMLButtonElement;
    expect(shell.classList).not.toContain('nav-hidden');
    expect(toggle.getAttribute('aria-expanded')).toBe('true');

    toggle.click();
    fixture.detectChanges();
    expect(shell.classList).toContain('nav-hidden');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(toggle.getAttribute('aria-controls')).toBe('shell-nav');

    toggle.click();
    fixture.detectChanges();
    expect(shell.classList).not.toContain('nav-hidden');
  });

  // On a phone the same button opens a drawer, which picking a section or Escape closes.
  it('opens the menu as a drawer on a phone and closes it with Escape', () => {
    const shell = fixture.componentInstance;
    shell.narrow.set(true);
    fixture.detectChanges();
    const toggle = fixture.nativeElement.querySelector('.nav-toggle') as HTMLButtonElement;
    expect(toggle.getAttribute('aria-expanded')).toBe('false');

    toggle.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.shell').classList).toContain('drawer-open');
    expect(fixture.nativeElement.querySelector('.nav-backdrop')).toBeTruthy();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.shell').classList).not.toContain('drawer-open');
    expect(fixture.nativeElement.querySelector('.nav-backdrop')).toBeNull();
    // The wide screen's choice is its own: opening a drawer never hid the sidebar.
    expect(shell.navHidden()).toBe(false);
  });
});

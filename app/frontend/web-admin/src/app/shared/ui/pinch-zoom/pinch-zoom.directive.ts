import { DestroyRef, Directive, ElementRef, Injector, afterNextRender, inject, output } from '@angular/core';

/** How much closer, and about which point of the screen (client pixels). */
export interface ZoomStep {
  factor: number;
  x: number;
  y: number;
}

/**
 * Zooming a map the way each device already zooms: two fingers pinching on the iPad, a pinch on
 * a trackpad, or Ctrl/⌘ and the mouse wheel. The maps had − and + buttons, and a person taking
 * measures with an iPad in one hand reached for the pinch first.
 *
 * <p>It only says how much closer and about which point; the map it sits on decides what that
 * means. Plain scrolling is left alone - one finger, two on a trackpad, the wheel - so a map that
 * scrolls still scrolls.
 *
 * <p>Safari sends its own gesture events for a pinch, on the iPad and on a Mac's trackpad, and
 * would zoom the whole page with them unless told not to; Chrome and Firefox send a trackpad's
 * pinch as a wheel with Ctrl held. A touch screen without Safari's events pinches with two
 * pointers.
 */
@Directive({
  selector: '[appPinchZoom]',
  host: { style: 'touch-action: pan-x pan-y' },
})
export class PinchZoomDirective {
  readonly zoomBy = output<ZoomStep>({ alias: 'appPinchZoom' });

  constructor() {
    const element = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
    const destroyRef = inject(DestroyRef);
    const on = <K extends string>(type: K, listener: (event: never) => void, options?: AddEventListenerOptions) => {
      const fn = listener as unknown as EventListener;
      element.addEventListener(type, fn, options);
      destroyRef.onDestroy(() => element.removeEventListener(type, fn, options));
    };
    const emit = (factor: number, x: number, y: number) => {
      if (Number.isFinite(factor) && factor > 0 && factor !== 1) this.zoomBy.emit({ factor, x, y });
    };

    on(
      'wheel',
      (event: WheelEvent) => {
        if (!event.ctrlKey && !event.metaKey) return;
        event.preventDefault();
        const unit = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? 400 : 1;
        // A trackpad sends many small steps, a wheel a few large ones: each at most a quarter closer.
        const delta = Math.max(-25, Math.min(25, event.deltaY * unit));
        emit(Math.exp(-delta * 0.01), event.clientX, event.clientY);
      },
      { passive: false },
    );

    let gesture = 0;
    let pinching = false;
    on(
      'gesturestart',
      (event: Event & { scale: number }) => {
        event.preventDefault();
        pinching = true;
        gesture = event.scale || 1;
      },
      { passive: false },
    );
    on(
      'gesturechange',
      (event: Event & { scale: number; clientX: number; clientY: number }) => {
        event.preventDefault();
        emit(event.scale / (gesture || 1), event.clientX, event.clientY);
        gesture = event.scale;
      },
      { passive: false },
    );
    on(
      'gestureend',
      (event: Event) => {
        event.preventDefault();
        pinching = false;
      },
      { passive: false },
    );

    const touches = new Map<number, { x: number; y: number }>();
    let span = 0;
    const measure = () => {
      const [a, b] = [...touches.values()];
      return { span: Math.hypot(a.x - b.x, a.y - b.y), x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
    };
    on('pointerdown', (event: PointerEvent) => {
      if (event.pointerType !== 'touch') return;
      touches.set(event.pointerId, { x: event.clientX, y: event.clientY });
      span = touches.size === 2 ? measure().span : 0;
    });
    on('pointermove', (event: PointerEvent) => {
      if (!touches.has(event.pointerId)) return;
      touches.set(event.pointerId, { x: event.clientX, y: event.clientY });
      if (touches.size !== 2 || pinching) return;
      const now = measure();
      if (span > 0 && now.span > 0) emit(now.span / span, now.x, now.y);
      span = now.span;
    });
    const lift = (event: PointerEvent) => {
      touches.delete(event.pointerId);
      span = touches.size === 2 ? measure().span : 0;
    };
    on('pointerup', lift);
    on('pointercancel', lift);
  }
}

/**
 * For a map drawn at a scale inside a box that scrolls: keeps the point being zoomed about under
 * the fingers, once the map has been drawn at its new size. Several steps of one pinch between two
 * frames are put back as one.
 */
export class ScrollZoom {
  private from: number | null = null;
  private at = { x: 0, y: 0 };
  private waiting = false;

  constructor(
    private readonly injector: Injector,
    private readonly box: () => HTMLElement | undefined,
    private readonly scale: () => number,
  ) {}

  /** Called before the scale changes, with the point to keep still. */
  around(x: number, y: number): void {
    this.at = { x, y };
    if (this.from === null) this.from = this.scale();
    if (this.waiting) return;
    this.waiting = true;
    afterNextRender(
      () => {
        this.waiting = false;
        const element = this.box();
        const from = this.from;
        this.from = null;
        if (element && from) keepPointUnder(element, from, this.scale(), this.at.x, this.at.y);
      },
      { injector: this.injector },
    );
  }
}

/** Scrolls `box` so that the point at client (x, y) stays put when what it holds grows by after / before. */
export function keepPointUnder(box: HTMLElement, before: number, after: number, x: number, y: number): void {
  const rect = box.getBoundingClientRect();
  const px = x - rect.left;
  const py = y - rect.top;
  const k = after / before;
  box.scrollLeft = (box.scrollLeft + px) * k - px;
  box.scrollTop = (box.scrollTop + py) * k - py;
}

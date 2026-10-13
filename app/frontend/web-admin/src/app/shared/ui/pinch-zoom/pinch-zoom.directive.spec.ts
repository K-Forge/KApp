import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { PinchZoomDirective, keepPointUnder, type ZoomStep } from './pinch-zoom.directive';

@Component({
  imports: [PinchZoomDirective],
  template: `<div class="map" (appPinchZoom)="steps.push($event)"></div>`,
})
class MapHost {
  readonly steps: ZoomStep[] = [];
}

describe('zooming a map with the hands', () => {
  function map() {
    const fixture = TestBed.createComponent(MapHost);
    fixture.detectChanges();
    return { host: fixture.componentInstance, element: fixture.nativeElement.querySelector('.map') as HTMLElement };
  }

  it('zooms with Ctrl or ⌘ and the wheel, about the pointer, and leaves a plain wheel to scroll', () => {
    const { host, element } = map();
    const plain = new WheelEvent('wheel', { deltaY: 40, cancelable: true });
    element.dispatchEvent(plain);
    expect(host.steps).toEqual([]);
    expect(plain.defaultPrevented).toBe(false);

    const closer = new WheelEvent('wheel', { deltaY: -10, ctrlKey: true, clientX: 30, clientY: 40, cancelable: true });
    element.dispatchEvent(closer);
    expect(closer.defaultPrevented).toBe(true);
    expect(host.steps[0].factor).toBeGreaterThan(1);
    expect(host.steps[0]).toMatchObject({ x: 30, y: 40 });

    element.dispatchEvent(new WheelEvent('wheel', { deltaY: 500, metaKey: true, cancelable: true }));
    // A wheel's big step is at most a quarter further out, however far it turned.
    expect(host.steps[1].factor).toBeCloseTo(Math.exp(-0.25), 5);
  });

  it('takes Safari’s pinch for itself, so the page does not zoom, and sends each change of it', () => {
    const { host, element } = map();
    const gesture = (type: string, scale: number) => {
      const event = Object.assign(new Event(type, { cancelable: true }), { scale, clientX: 5, clientY: 6 });
      element.dispatchEvent(event);
      return event;
    };
    expect(gesture('gesturestart', 1).defaultPrevented).toBe(true);
    gesture('gesturechange', 1.5);
    gesture('gesturechange', 3);
    gesture('gestureend', 3);
    expect(host.steps.map((s) => s.factor)).toEqual([1.5, 2]);
  });

  it('keeps the point being zoomed about under the fingers', () => {
    const box = document.createElement('div');
    Object.defineProperty(box, 'getBoundingClientRect', { value: () => ({ left: 100, top: 50 }) as DOMRect });
    let left = 200;
    let top = 80;
    Object.defineProperty(box, 'scrollLeft', { get: () => left, set: (v: number) => (left = v) });
    Object.defineProperty(box, 'scrollTop', { get: () => top, set: (v: number) => (top = v) });
    // At (150, 90) on screen: 50 px into the box, 250 px into what it scrolls; twice as large, 500.
    keepPointUnder(box, 1, 2, 150, 90);
    expect(left).toBe(450);
    expect(top).toBe(200);
  });
});

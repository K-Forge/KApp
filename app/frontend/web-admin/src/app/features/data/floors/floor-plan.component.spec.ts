import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import type { Point } from '../buildings/building.model';
import { rectangle, type DraftSpace } from './floor-draft';
import { FloorPlanComponent } from './floor-plan.component';

const ROOM: DraftSpace = {
  key: 'k-101',
  code: '101',
  name: 'Aula 101',
  typeCode: 'CLASSROOM',
  aliases: [],
  doors: [],
  shape: rectangle({ x: 100, y: 100, width: 200, height: 100 }),
};

function plan(): ComponentFixture<FloorPlanComponent> {
  const fixture = TestBed.createComponent(FloorPlanComponent);
  fixture.componentRef.setInput('width', 1000);
  fixture.componentRef.setInput('height', 800);
  fixture.componentRef.setInput('spaces', [ROOM]);
  fixture.componentRef.setInput('selectedKey', ROOM.key);
  fixture.detectChanges();
  return fixture;
}

/** A pointer event as the plan reads it, on the element found by `selector`, at a point of the drawing. */
function pointer(fixture: ComponentFixture<FloorPlanComponent>, selector: string, at: Point): PointerEvent {
  const target = (fixture.nativeElement as HTMLElement).querySelector(selector);
  if (!target) throw new Error(`nothing on the plan matches ${selector}`);
  return { target, clientX: at.x, clientY: at.y, pointerId: 1, pointerType: 'touch', button: 0 } as unknown as PointerEvent;
}

function tap(fixture: ComponentFixture<FloorPlanComponent>, selector: string, at: Point): void {
  fixture.componentInstance.onDown(pointer(fixture, selector, at));
  fixture.componentInstance.onUp(pointer(fixture, selector, at));
  fixture.detectChanges();
}

// The same gestures as the block editor, so that nobody has to learn two.
describe('reshaping a room on the floor plan', () => {
  it('adds a corner in the middle of a wall on a double tap of its square, and not on one tap', () => {
    const fixture = plan();
    const reshaped: { key: string; shape: Point[] }[] = [];
    fixture.componentInstance.reshaped.subscribe((r) => reshaped.push(r));

    // The top wall's square, at its middle.
    tap(fixture, '[data-edge="0"]', { x: 200, y: 100 });
    expect(reshaped).toEqual([]);
    tap(fixture, '[data-edge="0"]', { x: 200, y: 100 });

    expect(reshaped).toHaveLength(1);
    expect(reshaped[0].shape).toEqual([
      { x: 100, y: 100 },
      { x: 200, y: 100 },
      { x: 300, y: 100 },
      { x: 300, y: 200 },
      { x: 100, y: 200 },
    ]);
  });

  it('takes a corner away on a double tap', () => {
    const fixture = plan();
    const removed: { key: string; index: number }[] = [];
    fixture.componentInstance.vertexRemoved.subscribe((r) => removed.push(r));

    tap(fixture, '[data-vertex="2"]', { x: 300, y: 200 });
    tap(fixture, '[data-vertex="2"]', { x: 300, y: 200 });

    expect(removed).toEqual([{ key: ROOM.key, index: 2 }]);
  });

  it('pushes the wall out when its square is dragged, both corners together', () => {
    const fixture = plan();
    const reshaped: { key: string; shape: Point[] }[] = [];
    fixture.componentInstance.reshaped.subscribe((r) => reshaped.push(r));

    // The right wall's square, dragged 50 units to the right.
    fixture.componentInstance.onDown(pointer(fixture, '[data-edge="1"]', { x: 300, y: 150 }));
    fixture.componentInstance.onMove(pointer(fixture, '[data-edge="1"]', { x: 350, y: 160 }));
    fixture.componentInstance.onUp(pointer(fixture, '[data-edge="1"]', { x: 350, y: 160 }));

    expect(reshaped).toHaveLength(1);
    expect(reshaped[0].shape).toEqual(rectangle({ x: 100, y: 100, width: 250, height: 100 }));
  });

  it('does not take two taps on different squares for a double tap', () => {
    const fixture = plan();
    const reshaped: { key: string; shape: Point[] }[] = [];
    fixture.componentInstance.reshaped.subscribe((r) => reshaped.push(r));

    tap(fixture, '[data-edge="0"]', { x: 200, y: 100 });
    tap(fixture, '[data-edge="2"]', { x: 200, y: 200 });

    expect(reshaped).toEqual([]);
  });
});

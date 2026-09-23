import { ChangeDetectionStrategy, Component, ElementRef, computed, input, output, signal, viewChild } from '@angular/core';
import type { Corridor, GridPoint } from '../buildings/building.model';
import { CATEGORY_COLORS, type SpaceCategory } from '../spaces/space.model';
import { isUnidentified, label, rectOf, type DraftSpace, type Rect } from './floor-draft';

export type EditorMode = 'select' | 'box' | 'corridor';

/** Movement under this many pixels is a tap, not a drag - a finger never lands perfectly still. */
const TAP_SLOP = 8;

/**
 * The floor as a grid of cells, with its spaces as boxes and its corridors as lines.
 *
 * <p>Pointer Events rather than mouse or touch events, so a finger on the iPad, a pencil and a
 * mouse all go through the same code. Only drawing a box and drawing a corridor take the gesture
 * over (`touch-action: none`); selecting leaves panning to the browser, so a floor larger than
 * the screen still scrolls under a finger.
 */
@Component({
  selector: 'app-floor-grid',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      #surface
      class="surface"
      role="application"
      [attr.aria-label]="'Grid of ' + rows() + ' rows by ' + columns() + ' columns'"
      [class.drawing]="mode() !== 'select'"
      [class.disabled]="disabled()"
      [style.width.px]="columns() * cellSize()"
      [style.height.px]="rows() * cellSize()"
      [style.background-size]="cellSize() + 'px ' + cellSize() + 'px'"
      (pointerdown)="onDown($event)"
      (pointermove)="onMove($event)"
      (pointerup)="onUp($event)"
      (pointercancel)="reset()"
    >
      @for (box of boxes(); track box.space.key) {
        <div
          class="box"
          role="button"
          tabindex="0"
          [attr.data-key]="box.space.key"
          [class.selected]="box.space.key === selectedKey()"
          [class.unidentified]="box.unidentified"
          [class.problem]="problemKeys().has(box.space.key)"
          [style.left.px]="box.rect.col * cellSize()"
          [style.top.px]="box.rect.row * cellSize()"
          [style.width.px]="box.rect.colSpan * cellSize()"
          [style.height.px]="box.rect.rowSpan * cellSize()"
          [style.background]="box.color"
          [attr.aria-label]="box.label + ', row ' + box.rect.row + ', column ' + box.rect.col"
          [title]="box.label"
          [attr.aria-pressed]="box.space.key === selectedKey()"
          (click)="onBoxClick(box.space.key)"
          (keydown.enter)="onBoxClick(box.space.key)"
          (keydown.space)="$event.preventDefault(); onBoxClick(box.space.key)"
        >
          <span class="box-label" lang="es" [style.font-size.px]="labelSize()">{{ box.label }}</span>
        </div>
      }

      <svg class="corridors" [attr.width]="columns() * cellSize()" [attr.height]="rows() * cellSize()" aria-hidden="true">
        @for (corridor of corridors(); track $index; let i = $index) {
          <polyline
            [attr.points]="points(corridor)"
            [attr.stroke]="corridor.color"
            [attr.stroke-width]="cellSize() * (i === activeCorridor() ? 0.34 : 0.26)"
            [attr.opacity]="activeCorridor() === null || i === activeCorridor() ? 0.9 : 0.35"
          />
          @if (i === activeCorridor()) {
            @for (point of corridor.path; track $index; let last = $last) {
              <circle
                [attr.cx]="(point.col + 0.5) * cellSize()"
                [attr.cy]="(point.row + 0.5) * cellSize()"
                [attr.r]="cellSize() * (last ? 0.22 : 0.14)"
                [attr.fill]="corridor.color"
                class="corner"
              />
            }
          }
        }
      </svg>

      @if (preview(); as rect) {
        <div
          class="preview"
          [class.refused]="previewRefused()"
          [style.left.px]="rect.col * cellSize()"
          [style.top.px]="rect.row * cellSize()"
          [style.width.px]="rect.colSpan * cellSize()"
          [style.height.px]="rect.rowSpan * cellSize()"
        ></div>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .surface {
      --grid-line: color-mix(in srgb, var(--text) 14%, transparent);
      position: relative;
      background-color: var(--bg-elevated);
      background-image:
        linear-gradient(to right, var(--grid-line) 1px, transparent 1px),
        linear-gradient(to bottom, var(--grid-line) 1px, transparent 1px);
      border-right: 1px solid var(--grid-line);
      border-bottom: 1px solid var(--grid-line);
      touch-action: manipulation;
      user-select: none;
      -webkit-user-select: none;
      -webkit-touch-callout: none;
    }
    .surface.drawing {
      touch-action: none;
      cursor: crosshair;
    }
    .surface.disabled {
      pointer-events: none;
      opacity: 0.5;
    }
    .box {
      position: absolute;
      box-sizing: border-box;
      border: 1px solid rgb(28 33 40 / 45%);
      border-radius: 3px;
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
      padding: 1px;
      cursor: pointer;
      z-index: 1;
    }
    .box.unidentified {
      border-style: dashed;
      border-width: 2px;
    }
    .box.problem {
      box-shadow: inset 0 0 0 2px var(--danger);
    }
    .box.selected {
      outline: 3px solid var(--primary);
      outline-offset: 1px;
      z-index: 2;
    }
    .box:focus-visible {
      outline: 3px solid var(--focus-ring);
    }
    .box-label {
      /* The fills are pale in both themes, so the ink is fixed rather than the theme's text. */
      color: #1c2128;
      font-weight: 600;
      line-height: 1.1;
      text-align: center;
      /* Break long Spanish words at syllables - "Esca-leras" - rather than at any letter. */
      hyphens: auto;
      -webkit-hyphens: auto;
      overflow-wrap: break-word;
      pointer-events: none;
      /* A long name in a one-cell box ends in an ellipsis; the full one is in its title. */
      display: -webkit-box;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 3;
      line-clamp: 3;
      overflow: hidden;
    }
    /* Under the boxes: a corridor runs between rooms, and a room's label must stay readable. */
    .corridors {
      position: absolute;
      inset: 0;
      pointer-events: none;
      z-index: 0;
    }
    .corridors polyline {
      fill: none;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
    .corner {
      stroke: #fff;
      stroke-width: 1.5;
    }
    .preview {
      position: absolute;
      box-sizing: border-box;
      border: 2px dashed var(--primary);
      background: color-mix(in srgb, var(--primary) 18%, transparent);
      pointer-events: none;
      z-index: 3;
    }
    .preview.refused {
      border-color: var(--danger);
      background: color-mix(in srgb, var(--danger) 18%, transparent);
    }
  `,
})
export class FloorGridComponent {
  readonly rows = input.required<number>();
  readonly columns = input.required<number>();
  readonly cellSize = input(40);
  readonly spaces = input<DraftSpace[]>([]);
  readonly corridors = input<Corridor[]>([]);
  readonly categories = input<ReadonlyMap<string, SpaceCategory>>(new Map());
  readonly selectedKey = input<string | null>(null);
  readonly problemKeys = input<ReadonlySet<string>>(new Set());
  readonly activeCorridor = input<number | null>(null);
  readonly mode = input<EditorMode>('select');
  readonly disabled = input(false);
  /** Asked while a box is being drawn: whether the rectangle can take a new space. */
  readonly canDraw = input<(rect: Rect) => boolean>(() => true);

  readonly cellTap = output<GridPoint>();
  readonly boxTap = output<string>();
  readonly rectDrawn = output<Rect>();

  private readonly surface = viewChild.required<ElementRef<HTMLElement>>('surface');

  readonly boxes = computed(() =>
    this.spaces()
      .map((space) => ({ space, rect: rectOf(space) }))
      .filter((b): b is { space: DraftSpace; rect: Rect } => b.rect !== null)
      .map(({ space, rect }) => ({
        space,
        rect,
        label: label(space),
        unidentified: isUnidentified(space),
        color: CATEGORY_COLORS[this.categories().get(space.typeCode) ?? 'OTHER'],
      })),
  );

  /** Small cells get small labels; a big room keeps a readable one. */
  readonly labelSize = computed(() => Math.max(9, Math.min(13, Math.round(this.cellSize() * 0.28))));

  readonly preview = signal<Rect | null>(null);
  readonly previewRefused = computed(() => {
    const rect = this.preview();
    return rect !== null && !this.canDraw()(rect);
  });

  private start: { x: number; y: number; cell: GridPoint; pointerId: number } | null = null;

  points(corridor: Corridor): string {
    const size = this.cellSize();
    return corridor.path.map((p) => `${(p.col + 0.5) * size},${(p.row + 0.5) * size}`).join(' ');
  }

  onDown(event: PointerEvent): void {
    if (this.disabled() || (event.pointerType === 'mouse' && event.button !== 0)) return;
    const cell = this.cellAt(event);
    if (!cell) return;
    this.start = { x: event.clientX, y: event.clientY, cell, pointerId: event.pointerId };
    if (this.mode() === 'box' && !this.boxKeyAt(event)) {
      this.surface().nativeElement.setPointerCapture?.(event.pointerId);
      this.preview.set({ row: cell.row, col: cell.col, rowSpan: 1, colSpan: 1 });
    }
  }

  onMove(event: PointerEvent): void {
    if (!this.start || this.start.pointerId !== event.pointerId || !this.preview()) return;
    const cell = this.cellAt(event, true);
    if (!cell) return;
    const from = this.start.cell;
    this.preview.set({
      row: Math.min(from.row, cell.row),
      col: Math.min(from.col, cell.col),
      rowSpan: Math.abs(cell.row - from.row) + 1,
      colSpan: Math.abs(cell.col - from.col) + 1,
    });
  }

  onUp(event: PointerEvent): void {
    const start = this.start;
    if (!start || start.pointerId !== event.pointerId) return;
    const drawn = this.preview();
    const moved = Math.hypot(event.clientX - start.x, event.clientY - start.y) > TAP_SLOP;
    this.reset();

    if (drawn) {
      if (this.canDraw()(drawn)) this.rectDrawn.emit(drawn);
      return;
    }
    if (moved) return;

    // A tap on a box is its click's to handle - the click is also what a screen reader sends -
    // except while drawing a corridor, which runs through whatever cell is tapped.
    if (this.mode() !== 'corridor' && this.boxKeyAt(event)) return;
    this.cellTap.emit(start.cell);
  }

  onBoxClick(key: string): void {
    if (!this.disabled() && this.mode() !== 'corridor') this.boxTap.emit(key);
  }

  reset(): void {
    this.start = null;
    this.preview.set(null);
  }

  private boxKeyAt(event: PointerEvent): string | null {
    const target = event.target as HTMLElement | null;
    return target?.closest<HTMLElement>('.box')?.dataset['key'] ?? null;
  }

  /** The cell under the pointer; clamped to the grid while a drag strays outside it. */
  private cellAt(event: PointerEvent, clamp = false): GridPoint | null {
    const bounds = this.surface().nativeElement.getBoundingClientRect();
    const size = this.cellSize();
    let row = Math.floor((event.clientY - bounds.top) / size);
    let col = Math.floor((event.clientX - bounds.left) / size);
    if (clamp) {
      row = Math.max(0, Math.min(this.rows() - 1, row));
      col = Math.max(0, Math.min(this.columns() - 1, col));
    }
    return row >= 0 && col >= 0 && row < this.rows() && col < this.columns() ? { row, col } : null;
  }
}

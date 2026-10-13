import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ACCESSIBILITY, ACCESSIBILITY_LABELS, type Accessibility, type Wing } from '../buildings/building.model';
import { CATEGORY_LABELS, SPACE_CATEGORIES, type Door, type SpaceType } from '../spaces/space.model';
import { boundsOf, isPlaced, isUnidentified, label, type DraftSpace } from './floor-draft';
import type { LayoutSpace } from './floor.model';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { t } from '../../../core/i18n/i18n.service';

export interface CirculationOption {
  code: string;
  name: string;
  floorCode: string;
}

/**
 * Everything about the selected space, edited in place. Each field applies when it is left, not
 * on every keystroke, so one undo takes back one edit rather than one letter.
 *
 * <p>Moving is buttons as well as a gesture: on a phone a small room is smaller than a fingertip,
 * and a button is what lands where it was meant to. Its outline is reshaped on the plan itself,
 * by its corners, and its doors are put on its walls there too.
 */
@Component({
  selector: 'app-space-inspector',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let s = space();
    <div class="stack inspector">
      @if (unidentified() && unplaced().length) {
        <div class="field match">
          <label for="i-assign">{{ 'This box is…' | t }}</label>
          <select id="i-assign" (change)="onAssign($event)">
            <option value="">{{ 'pick a space from the inventory…' | t }}</option>
            @for (option of unplaced(); track option.key) {
              <option [value]="option.key">{{ optionLabel(option) }}</option>
            }
          </select>
          <span class="hint">{{ 'The inventoried space takes this box’s outline and doors, and the empty box goes.' | t }}</span>
        </div>
      }

      <div class="row spread">
        <div class="field" style="flex: 1 1 7rem">
          <label for="i-door">{{ 'Number on the door' | t }}</label>
          <input id="i-door" type="text" [value]="s.doorCode ?? ''" placeholder="503-S" (change)="text('doorCode', $event)" />
        </div>
        <div class="field" style="flex: 1 1 7rem">
          <label for="i-code">{{ 'Internal code' | t }}</label>
          <input id="i-code" type="text" [value]="s.code" (change)="text('code', $event)" />
        </div>
      </div>

      <div class="field">
        <label for="i-name">{{ 'Name' | t }}</label>
        <input id="i-name" type="text" [value]="s.name" (change)="text('name', $event)" />
      </div>

      <div class="row spread">
        <div class="field" style="flex: 1 1 9rem">
          <label for="i-type">{{ 'Type' | t }}</label>
          <select id="i-type" (change)="select('typeCode', $event)">
            @for (group of typeGroups(); track group.category) {
              <optgroup [label]="group.label | t">
                @for (type of group.types; track type.code) {
                  <option [value]="type.code" [selected]="type.code === s.typeCode">{{ type.name }}</option>
                }
              </optgroup>
            }
          </select>
        </div>
        @if (wings().length) {
          <div class="field" style="flex: 1 1 7rem">
            <label for="i-wing">{{ 'Wing' | t }}</label>
            <select id="i-wing" (change)="select('wing', $event)">
              <option value="" [selected]="!s.wing">{{ 'No wing' | t }}</option>
              @for (wing of wings(); track wing.code) {
                <option [value]="wing.code" [selected]="wing.code === s.wing">{{ wing.name }}</option>
              }
            </select>
          </div>
        }
      </div>

      <div class="place">
        @if (placed()) {
          <span class="text-muted">
            {{ s.doors.length === 1 ? ('{size} · {corners} corners · 1 door' | t: { size: size(), corners: s.shape?.length }) : ('{size} · {corners} corners · {doors} doors' | t: { size: size(), corners: s.shape?.length, doors: s.doors.length }) }}
          </span>
          <p class="hint">
            {{ 'Drag it to move it. Drag a corner, or the square on a wall to push the wall out; double-tap a corner to take it away, a wall’s square to add one.' | t }}
          </p>
          <div class="pads">
            <div class="pad" role="group" [attr.aria-label]="'Move' | t">
              <span class="pad-title">{{ 'Move' | t }}</span>
              <button type="button" class="btn btn-sm up" [attr.aria-label]="'Move up' | t" (click)="nudge.emit({ dx: 0, dy: -step() })">↑</button>
              <button type="button" class="btn btn-sm left" [attr.aria-label]="'Move left' | t" (click)="nudge.emit({ dx: -step(), dy: 0 })">←</button>
              <button type="button" class="btn btn-sm right" [attr.aria-label]="'Move right' | t" (click)="nudge.emit({ dx: step(), dy: 0 })">→</button>
              <button type="button" class="btn btn-sm down" [attr.aria-label]="'Move down' | t" (click)="nudge.emit({ dx: 0, dy: step() })">↓</button>
            </div>
            <div class="doors">
              <span class="pad-title">{{ 'Doors' | t }}</span>
              <button type="button" class="btn btn-sm" [class.btn-primary]="doorMode()" (click)="doors.emit()">
                {{ doorMode() ? ('Tap a wall… (done)' | t) : ('Put doors on its walls' | t) }}
              </button>
              @for (door of s.doors; track $index) {
                <div class="row door-row">
                  <span class="text-muted">{{ 'Door {value} · {value2} wide' | t: { value: $index + 1, value2: doorLength(door) } }}</span>
                  <button type="button" class="btn btn-sm" [attr.aria-label]="('Remove door ' | t) + ($index + 1)" (click)="removeDoor.emit($index)">✕</button>
                </div>
              }
            </div>
          </div>
          <div class="row">
            <button type="button" class="btn btn-sm" [class.btn-primary]="splitMode()" (click)="split.emit()">
              {{ splitMode() ? ('Tap where to cut… (cancel)' | t) : ('Split in two' | t) }}
            </button>
            <button type="button" class="btn btn-sm" (click)="unplace.emit()">{{ 'Back to the inventory' | t }}</button>
          </div>
        } @else {
          <p class="text-muted" style="margin:0">{{ 'Not drawn yet.' | t }}</p>
          <button type="button" class="btn btn-sm" [class.btn-primary]="placing()" (click)="move.emit()">
            {{ placing() ? ('Now drag its outline on the plan, or tap where it is…' | t) : ('Draw it on the plan' | t) }}
          </button>
        }
      </div>

      <div class="row spread">
        <div class="field" style="flex: 1 1 9rem">
          <label for="i-via">{{ 'Reached via' | t }}</label>
          <select id="i-via" (change)="select('accessVia', $event)">
            <option value="" [selected]="!s.accessVia">{{ 'Nothing in particular' | t }}</option>
            @for (option of circulationOptions(); track option.code) {
              <option [value]="option.code" [selected]="option.code === s.accessVia">{{ option.name }} ({{ option.floorCode }})</option>
            }
          </select>
        </div>
        <div class="field" style="flex: 1 1 9rem">
          <label for="i-access">{{ 'Without stairs?' | t }}</label>
          <select id="i-access" (change)="select('accessibility', $event)">
            <option value="" [selected]="!s.accessibility">{{ 'Same as the floor ({floorAccessibilityLabel})' | t: { floorAccessibilityLabel: floorAccessibilityLabel() } }}</option>
            @for (value of accessibility; track value) {
              <option [value]="value" [selected]="value === s.accessibility">{{ accessibilityLabels[value] | t }}</option>
            }
          </select>
        </div>
      </div>

      <div class="field">
        <label for="i-note">{{ 'How to get there' | t }}</label>
        <input id="i-note" type="text" [value]="s.note ?? ''" [placeholder]="'Solo por la escalera norte, desde el P5' | t" (change)="text('note', $event)" />
      </div>

      <div class="row spread">
        <div class="field" style="flex: 2 1 10rem">
          <label for="i-aliases">{{ 'Other names' | t }}</label>
          <textarea id="i-aliases" rows="2" [value]="s.aliases.join('\\n')" placeholder="S-503" (change)="aliases($event)"></textarea>
          <span class="hint">{{ 'One per line.' | t }}</span>
        </div>
        <div class="field" style="flex: 1 1 5rem">
          <label for="i-capacity">{{ 'Capacity' | t }}</label>
          <input id="i-capacity" type="number" min="0" [value]="s.capacity ?? ''" (change)="capacity($event)" />
        </div>
      </div>

      @if (issues().length) {
        <ul class="issues" role="alert">
          @for (issue of issues(); track issue) {
            <li>{{ issue }}</li>
          }
        </ul>
      }

      <button type="button" class="btn btn-sm btn-danger" (click)="remove.emit()">{{ 'Delete this space' | t }}</button>
    </div>
  `,
  styles: `
    .inspector .field {
      margin-bottom: 0;
    }
    .match {
      padding: 0.5rem;
      border: 1px dashed var(--primary);
      border-radius: var(--radius-sm);
    }
    .place {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      padding: 0.5rem;
      background: var(--bg-inset);
      border-radius: var(--radius-sm);
    }
    .hint {
      margin: 0;
      font-size: 0.75rem;
      color: var(--text-faint);
    }
    .doors {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      min-width: 10rem;
    }
    .door-row {
      justify-content: space-between;
      align-items: center;
    }
    .pads {
      display: flex;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .pad {
      display: grid;
      grid-template-columns: repeat(3, 2.75rem);
      grid-template-rows: auto repeat(3, 2.75rem);
      gap: 0.25rem;
    }
    .pad .btn {
      min-height: 2.75rem;
      padding: 0;
      font-size: 1.125rem;
    }
    .pad-title {
      grid-column: 1 / -1;
      font-size: 0.75rem;
      font-weight: 600;
      color: var(--text-muted);
    }
    .pad .up { grid-column: 2; grid-row: 2; }
    .pad .left { grid-column: 1; grid-row: 3; }
    .pad .right { grid-column: 3; grid-row: 3; }
    .pad .down { grid-column: 2; grid-row: 4; }
    .issues {
      margin: 0;
      padding-left: 1.1rem;
      color: var(--danger);
      font-size: 0.8125rem;
    }
  `,
})
export class SpaceInspectorComponent {
  readonly space = input.required<DraftSpace>();
  readonly types = input<SpaceType[]>([]);
  readonly wings = input<Wing[]>([]);
  readonly circulation = input<CirculationOption[]>([]);
  readonly unplaced = input<DraftSpace[]>([]);
  readonly floorAccessibility = input<Accessibility>('UNKNOWN');
  readonly issues = input<string[]>([]);
  readonly placing = input(false);
  readonly doorMode = input(false);
  readonly splitMode = input(false);
  /** How far one press of a Move button goes, in the drawing's units. */
  readonly step = input(1);

  readonly patch = output<Partial<LayoutSpace>>();
  readonly nudge = output<{ dx: number; dy: number }>();
  readonly move = output<void>();
  readonly doors = output<void>();
  readonly removeDoor = output<number>();
  /** Where the plan drew one room and the floor has two. */
  readonly split = output<void>();
  readonly unplace = output<void>();
  readonly remove = output<void>();
  readonly assign = output<string>();

  readonly accessibility = ACCESSIBILITY;
  readonly accessibilityLabels = ACCESSIBILITY_LABELS;

  readonly placed = computed(() => isPlaced(this.space()));
  readonly unidentified = computed(() => this.placed() && isUnidentified(this.space()));
  readonly size = computed(() => {
    const shape = this.space().shape;
    if (!shape?.length) return '';
    const box = boundsOf(shape);
    return `${box.width} x ${box.height}`;
  });
  readonly floorAccessibilityLabel = computed(() => t(ACCESSIBILITY_LABELS[this.floorAccessibility()]).toLowerCase());

  readonly typeGroups = computed(() =>
    SPACE_CATEGORIES.map((category) => ({
      category,
      label: CATEGORY_LABELS[category],
      types: this.types().filter((t) => t.category === category),
    })).filter((group) => group.types.length > 0),
  );

  /** A space is never offered as its own way in, and a stale target stays visible. */
  readonly circulationOptions = computed(() => {
    const own = this.space().code;
    const options = this.circulation().filter((o) => o.code !== own);
    const current = this.space().accessVia;
    return current && !options.some((o) => o.code === current)
      ? [...options, { code: current, name: t('{current} - not found', { current }), floorCode: '?' }]
      : options;
  });

  doorLength(door: Door): number {
    return Math.round(Math.hypot(door.to.x - door.from.x, door.to.y - door.from.y));
  }

  optionLabel(space: DraftSpace): string {
    const door = space.doorCode?.trim();
    return door && door !== space.name ? `${door} — ${space.name}` : label(space);
  }

  text(field: 'doorCode' | 'code' | 'name' | 'note', event: Event): void {
    const value = (event.target as HTMLInputElement).value.trim();
    this.patch.emit({ [field]: field === 'code' || field === 'name' ? value : value || null });
  }

  select(field: 'typeCode' | 'wing' | 'accessVia' | 'accessibility', event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.patch.emit({ [field]: field === 'typeCode' ? value : value || null } as Partial<LayoutSpace>);
  }

  aliases(event: Event): void {
    const value = (event.target as HTMLTextAreaElement).value;
    this.patch.emit({ aliases: value.split('\n').map((a) => a.trim()).filter(Boolean) });
  }

  capacity(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.patch.emit({ capacity: value === '' ? null : Math.max(0, Math.round(Number(value))) });
  }

  onAssign(event: Event): void {
    const key = (event.target as HTMLSelectElement).value;
    if (key) this.assign.emit(key);
  }
}

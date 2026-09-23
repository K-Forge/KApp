import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import type { Wing } from '../buildings/building.model';
import { CATEGORY_LABELS, SPACE_CATEGORIES, type SpaceType } from '../spaces/space.model';
import { label, type DraftSpace, type RangeRequest } from './floor-draft';

export interface OneSpace {
  doorCode: string;
  name: string;
  typeCode: string;
  wing: string | null;
}

/**
 * The spaces known to be on this floor but not yet drawn: what an information plaque lists,
 * before anybody has walked the floor to say which box each one is.
 *
 * <p>Tap one to select it, then tap a cell to put it there - or select an empty box drawn from
 * the evacuation plan and say which of these it is.
 */
@Component({
  selector: 'app-inventory-tray',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      @if (spaces().length) {
        <ul class="tray" aria-label="Spaces not on the grid yet">
          @for (space of spaces(); track space.key) {
            <li>
              <button
                type="button"
                class="tray-item"
                [class.selected]="space.key === selectedKey()"
                [attr.aria-pressed]="space.key === selectedKey()"
                (click)="pick.emit(space.key)"
              >
                <span class="tray-label">{{ label(space) }}</span>
                @if (space.doorCode && space.name !== space.doorCode) {
                  <span class="text-faint">{{ space.name }}</span>
                }
                <span class="text-faint tray-type">{{ typeName(space.typeCode) }}</span>
              </button>
            </li>
          }
        </ul>
        <p class="text-faint note">Tap one, then tap a cell - or select an empty box and say which of these it is.</p>
      } @else {
        <p class="text-muted note">Everything on this floor has a place on the grid.</p>
      }

      <details [open]="!spaces().length">
        <summary>Add one space</summary>
        <div class="stack add">
          <div class="row spread">
            <div class="field" style="flex: 1 1 6rem">
              <label for="t-door">Number on the door</label>
              <input id="t-door" type="text" placeholder="503-S" [value]="oneDoor()" (input)="oneDoor.set(value($event))" />
            </div>
            <div class="field" style="flex: 2 1 9rem">
              <label for="t-name">Name</label>
              <input id="t-name" type="text" placeholder="Aula 503" [value]="oneName()" (input)="oneName.set(value($event))" />
            </div>
          </div>
          <div class="row spread">
            <div class="field" style="flex: 2 1 9rem">
              <label for="t-type">Type</label>
              <select id="t-type" (change)="oneType.set(value($event))">
                @for (group of typeGroups(); track group.category) {
                  <optgroup [label]="group.label">
                    @for (type of group.types; track type.code) {
                      <option [value]="type.code" [selected]="type.code === oneType()">{{ type.name }}</option>
                    }
                  </optgroup>
                }
              </select>
            </div>
            @if (wings().length) {
              <div class="field" style="flex: 1 1 6rem">
                <label for="t-wing">Wing</label>
                <select id="t-wing" (change)="oneWing.set(value($event))">
                  <option value="">No wing</option>
                  @for (wing of wings(); track wing.code) {
                    <option [value]="wing.code" [selected]="wing.code === oneWing()">{{ wing.name }}</option>
                  }
                </select>
              </div>
            }
          </div>
          <button type="button" class="btn btn-sm" [disabled]="!oneName().trim() && !oneDoor().trim()" (click)="submitOne()">
            Add to the inventory
          </button>
        </div>
      </details>

      <details>
        <summary>Add a range from a plaque</summary>
        <div class="stack add">
          <p class="text-faint note">For "401 a 410": ten spaces, 401 to 410, none of them placed yet.</p>
          <div class="row spread">
            <div class="field" style="flex: 1 1 5rem">
              <label for="r-from">From</label>
              <input id="r-from" type="number" min="0" [value]="rangeFrom()" (input)="rangeFrom.set(number($event))" />
            </div>
            <div class="field" style="flex: 1 1 5rem">
              <label for="r-to">To</label>
              <input id="r-to" type="number" min="0" [value]="rangeTo()" (input)="rangeTo.set(number($event))" />
            </div>
            @if (wings().length) {
              <div class="field" style="flex: 1 1 7rem">
                <label for="r-wing">Wing</label>
                <select id="r-wing" (change)="rangeWing.set(value($event))">
                  <option value="">No wing</option>
                  @for (wing of wings(); track wing.code) {
                    <option [value]="wing.code" [selected]="wing.code === rangeWing()">{{ wing.name }}{{ wing.doorSuffix ? ' (' + wing.doorSuffix + ')' : '' }}</option>
                  }
                </select>
              </div>
            }
          </div>
          <div class="row spread">
            <div class="field" style="flex: 2 1 9rem">
              <label for="r-name">Name</label>
              <input id="r-name" type="text" [value]="rangeName()" (input)="rangeName.set(value($event))" />
              <span class="hint">{{ '{' }}n{{ '}' }} is replaced by each number, without the wing's suffix.</span>
            </div>
            <div class="field" style="flex: 2 1 9rem">
              <label for="r-type">Type</label>
              <select id="r-type" (change)="rangeType.set(value($event))">
                @for (group of typeGroups(); track group.category) {
                  <optgroup [label]="group.label">
                    @for (type of group.types; track type.code) {
                      <option [value]="type.code" [selected]="type.code === rangeType()">{{ type.name }}</option>
                    }
                  </optgroup>
                }
              </select>
            </div>
          </div>
          <button type="button" class="btn btn-sm" [disabled]="rangeCount() < 1 || rangeCount() > 100" (click)="submitRange()">
            @if (rangeCount() > 100) {
              At most 100 at once
            } @else {
              Add {{ rangeCount() }} space{{ rangeCount() === 1 ? '' : 's' }}: {{ rangePreview() }}
            }
          </button>
        </div>
      </details>
    </div>
  `,
  styles: `
    .tray {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      max-height: 22rem;
      overflow-y: auto;
    }
    .tray-item {
      width: 100%;
      min-height: 2.75rem;
      display: flex;
      align-items: baseline;
      gap: 0.5rem;
      text-align: left;
      padding: 0.5rem 0.625rem;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      background: var(--bg-elevated);
      color: var(--text);
    }
    .tray-item.selected {
      border-color: var(--primary);
      box-shadow: inset 0 0 0 1px var(--primary);
    }
    .tray-label {
      font-weight: 600;
    }
    .tray-type {
      margin-left: auto;
      font-size: 0.75rem;
    }
    .note {
      margin: 0;
      font-size: 0.8125rem;
    }
    details summary {
      cursor: pointer;
      font-weight: 600;
      min-height: 2.25rem;
      display: flex;
      align-items: center;
    }
    .add .field {
      margin-bottom: 0;
    }
  `,
})
export class InventoryTrayComponent {
  readonly spaces = input<DraftSpace[]>([]);
  readonly types = input<SpaceType[]>([]);
  readonly wings = input<Wing[]>([]);
  readonly selectedKey = input<string | null>(null);

  readonly pick = output<string>();
  readonly addOne = output<OneSpace>();
  readonly addRange = output<RangeRequest>();

  readonly label = label;

  readonly oneDoor = signal('');
  readonly oneName = signal('');
  readonly oneType = signal('CLASSROOM');
  readonly oneWing = signal('');

  readonly rangeFrom = signal(401);
  readonly rangeTo = signal(410);
  readonly rangeWing = signal('');
  readonly rangeName = signal('Aula {n}');
  readonly rangeType = signal('CLASSROOM');

  readonly typeGroups = computed(() =>
    SPACE_CATEGORIES.map((category) => ({
      category,
      label: CATEGORY_LABELS[category],
      types: this.types().filter((t) => t.category === category),
    })).filter((group) => group.types.length > 0),
  );

  readonly rangeSuffix = computed(() => this.wings().find((w) => w.code === this.rangeWing())?.doorSuffix ?? '');
  readonly rangeCount = computed(() => {
    const from = this.rangeFrom();
    const to = this.rangeTo();
    return Number.isFinite(from) && Number.isFinite(to) ? Math.abs(to - from) + 1 : 0;
  });
  readonly rangePreview = computed(() => {
    const [low, high] = [Math.min(this.rangeFrom(), this.rangeTo()), Math.max(this.rangeFrom(), this.rangeTo())];
    const suffix = this.rangeSuffix();
    return low === high ? `${low}${suffix}` : `${low}${suffix} … ${high}${suffix}`;
  });

  typeName(code: string): string {
    return this.types().find((t) => t.code === code)?.name ?? code;
  }

  value(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  number(event: Event): number {
    return Math.trunc(Number((event.target as HTMLInputElement).value));
  }

  submitOne(): void {
    const door = this.oneDoor().trim();
    this.addOne.emit({ doorCode: door, name: this.oneName().trim() || door, typeCode: this.oneType(), wing: this.oneWing() || null });
    this.oneDoor.set('');
    this.oneName.set('');
  }

  submitRange(): void {
    this.addRange.emit({
      from: this.rangeFrom(),
      to: this.rangeTo(),
      suffix: this.rangeSuffix(),
      wing: this.rangeWing() || null,
      namePattern: this.rangeName(),
      typeCode: this.rangeType(),
    });
  }
}

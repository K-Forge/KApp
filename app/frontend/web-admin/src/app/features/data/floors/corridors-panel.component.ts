import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import type { Corridor } from '../buildings/building.model';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/**
 * The corridors of the floor: the routes people walk, in the colour the building paints them.
 * One is drawn at a time - pick it, then tap the cells it runs through in walking order.
 */
@Component({
  selector: 'app-corridors-panel',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      @for (corridor of corridors(); track $index; let i = $index) {
        <div class="corridor" [class.active]="i === active()">
          <div class="row">
            <input
              type="color"
              class="swatch"
              [value]="corridor.color"
              [attr.aria-label]="('Colour of ' | t) + corridor.name"
              (change)="changed.emit({ index: i, patch: { color: value($event) } })"
            />
            <input
              type="text"
              [value]="corridor.name"
              [attr.aria-label]="('Name of corridor ' | t) + (i + 1)"
              (change)="changed.emit({ index: i, patch: { name: value($event).trim() } })"
            />
          </div>
          <div class="row-between">
            <span class="text-faint small">{{ corridor.code }} · {{ corridor.path.length === 1 ? ('1 point' | t) : ('{points} points' | t: { points: corridor.path.length }) }}</span>
            <div class="row">
              <button type="button" class="btn btn-sm" [class.btn-primary]="i === active()" (click)="activate.emit(i === active() ? null : i)">
                {{ i === active() ? ('Done' | t) : ('Draw' | t) }}
              </button>
              <button type="button" class="btn btn-sm btn-danger" (click)="remove.emit(i)">{{ 'Delete' | t }}</button>
            </div>
          </div>
        </div>
      } @empty {
        <p class="text-muted small" style="margin:0">{{ 'No corridors on this floor yet.' | t }}</p>
      }

      @if (active() !== null) {
        <p class="small hint-active">{{ 'Tap the cells it runs through, in walking order. Tap one again to take it out.' | t }}</p>
      }

      <details>
        <summary>{{ 'New corridor' | t }}</summary>
        <div class="stack add">
          <div class="row spread">
            <div class="field" style="flex: 1 1 7rem">
              <label for="c-code">{{ 'Code' | t }}</label>
              <input id="c-code" type="text" [value]="code()" placeholder="PAS-CENTRAL" (input)="code.set(value($event))" />
            </div>
            <div class="field" style="flex: 2 1 9rem">
              <label for="c-name">{{ 'Name' | t }}</label>
              <input id="c-name" type="text" [value]="name()" [placeholder]="'Pasillo central' | t" (input)="name.set(value($event))" />
            </div>
            <div class="field" style="flex: 0 0 4rem">
              <label for="c-color">{{ 'Colour' | t }}</label>
              <input id="c-color" type="color" class="swatch" [value]="color()" (input)="color.set(value($event))" />
            </div>
          </div>
          <button type="button" class="btn btn-sm" [disabled]="!code().trim() || !name().trim()" (click)="submit()">
            {{ 'Create and start drawing' | t }}
          </button>
        </div>
      </details>
    </div>
  `,
  styles: `
    .corridor {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
      padding: 0.5rem;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
    }
    .corridor.active {
      border-color: var(--primary);
      box-shadow: inset 0 0 0 1px var(--primary);
    }
    .swatch {
      width: 2.75rem;
      min-width: 2.75rem;
      height: 2.5rem;
      padding: 2px;
    }
    .small {
      font-size: 0.8125rem;
    }
    .hint-active {
      margin: 0;
      color: var(--primary-strong);
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
export class CorridorsPanelComponent {
  readonly corridors = input<Corridor[]>([]);
  readonly active = input<number | null>(null);

  readonly activate = output<number | null>();
  readonly remove = output<number>();
  readonly changed = output<{ index: number; patch: Partial<Corridor> }>();
  readonly create = output<Corridor>();

  readonly code = signal('');
  readonly name = signal('');
  readonly color = signal('#5B8DEF');

  value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  submit(): void {
    this.create.emit({ code: this.code().trim().toUpperCase(), name: this.name().trim(), color: this.color().toUpperCase(), path: [] });
    this.code.set('');
    this.name.set('');
  }
}

import { ChangeDetectionStrategy, Component, effect, input, output } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  ACCESSIBILITY,
  ACCESSIBILITY_LABELS,
  FLOOR_STATUSES,
  FLOOR_STATUS_LABELS,
  type Accessibility,
  type Building,
  type BuildingRequest,
  type Floor,
  type FloorStatus,
  type Wing,
} from './building.model';

const CODE = /^[A-Z0-9]{1,8}$/;

type FloorForm = FormGroup<{
  code: FormControl<string>;
  level: FormControl<number>;
  name: FormControl<string>;
  status: FormControl<FloorStatus>;
  accessibility: FormControl<Accessibility>;
  note: FormControl<string>;
  gridRows: FormControl<number>;
  gridColumns: FormControl<number>;
}>;

type WingForm = FormGroup<{
  code: FormControl<string>;
  name: FormControl<string>;
  doorSuffix: FormControl<string>;
  note: FormControl<string>;
}>;

type BuildingForm = FormGroup<{
  code: FormControl<string>;
  name: FormControl<string>;
  campus: FormControl<string>;
  description: FormControl<string>;
  aliases: FormControl<string>;
  wings: FormArray<WingForm>;
  floors: FormArray<FloorForm>;
}>;

function floorGroup(floor?: Partial<Floor>): FloorForm {
  return new FormGroup({
    code: new FormControl(floor?.code ?? '', { nonNullable: true, validators: [Validators.required, Validators.pattern(CODE)] }),
    // Decimal: a mezzanine sits at 1.5, between the two floors it is between.
    level: new FormControl(floor?.level ?? 1, { nonNullable: true, validators: [Validators.required, Validators.min(-5), Validators.max(99)] }),
    name: new FormControl(floor?.name ?? '', { nonNullable: true, validators: [Validators.required, Validators.maxLength(60)] }),
    status: new FormControl<FloorStatus>(floor?.status ?? 'UNMAPPED', { nonNullable: true }),
    accessibility: new FormControl<Accessibility>(floor?.accessibility ?? 'UNKNOWN', { nonNullable: true }),
    note: new FormControl(floor?.note ?? '', { nonNullable: true, validators: [Validators.maxLength(300)] }),
    gridRows: new FormControl(floor?.gridRows ?? 11, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(60)] }),
    gridColumns: new FormControl(floor?.gridColumns ?? 16, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(60)] }),
  });
}

function wingGroup(wing?: Partial<Wing>): WingForm {
  return new FormGroup({
    code: new FormControl(wing?.code ?? '', { nonNullable: true, validators: [Validators.required, Validators.pattern(CODE)] }),
    name: new FormControl(wing?.name ?? '', { nonNullable: true, validators: [Validators.required, Validators.maxLength(60)] }),
    doorSuffix: new FormControl(wing?.doorSuffix ?? '', { nonNullable: true, validators: [Validators.maxLength(4)] }),
    note: new FormControl(wing?.note ?? '', { nonNullable: true, validators: [Validators.maxLength(300)] }),
  });
}

/**
 * Create/edit form for a building: its aliases, its wings and its floors. Constraints come
 * straight from BuildingRequest in docs/api/map.openapi.yaml, so the form is never stricter or
 * looser than the server.
 */
@Component({
  selector: 'app-building-form',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" class="stack">
      <div class="field" [class.invalid]="invalid('code')">
        <label for="b-code">Code</label>
        <input id="b-code" type="text" formControlName="code" [readonly]="editing()" placeholder="EC" />
        @if (invalid('code')) {
          <span class="error">Required, 1-10 characters.</span>
        }
      </div>

      <div class="field" [class.invalid]="invalid('name')">
        <label for="b-name">Name</label>
        <input id="b-name" type="text" formControlName="name" placeholder="Edificio Central" />
        @if (invalid('name')) {
          <span class="error">Required, 1-120 characters.</span>
        }
      </div>

      <div class="field" [class.invalid]="invalid('campus')">
        <label for="b-campus">Campus</label>
        <input id="b-campus" type="text" formControlName="campus" />
        @if (invalid('campus')) {
          <span class="error">Required, 1-120 characters.</span>
        }
      </div>

      <div class="field">
        <label for="b-aliases">Other names</label>
        <textarea id="b-aliases" rows="2" formControlName="aliases" placeholder="Bienestar&#10;Edificio de bienestar"></textarea>
        <span class="hint">One per line. What people call the building besides its name - the search matches them.</span>
      </div>

      <div class="field">
        <label for="b-description">Description</label>
        <textarea id="b-description" rows="2" formControlName="description"></textarea>
        <span class="hint">Optional, up to 500 characters.</span>
      </div>

      <div class="row-between">
        <h3 style="margin:0">Wings</h3>
        <button type="button" class="btn btn-sm" (click)="addWing()">Add wing</button>
      </div>
      <p class="form-note">
        Leave empty for a building with one body. The door suffix is what the doors of that wing
        append to the number: <code>-S</code> for <code>503-S</code>. Leave it empty when they append nothing.
      </p>
      @for (wing of form.controls.wings.controls; track $index) {
        <div class="card row-card" [formGroup]="wing">
          <div class="cells">
            <div class="field">
              <label [for]="'w-code-' + $index">Code</label>
              <input [id]="'w-code-' + $index" type="text" formControlName="code" placeholder="S" />
            </div>
            <div class="field">
              <label [for]="'w-name-' + $index">Name</label>
              <input [id]="'w-name-' + $index" type="text" formControlName="name" placeholder="Ala sur" />
            </div>
            <div class="field">
              <label [for]="'w-suffix-' + $index">Door suffix</label>
              <input [id]="'w-suffix-' + $index" type="text" formControlName="doorSuffix" placeholder="-S" />
            </div>
          </div>
          <div class="field">
            <label [for]="'w-note-' + $index">How to get in or across</label>
            <input [id]="'w-note-' + $index" type="text" formControlName="note" placeholder="Se cruza por el P1 o por la terraza" />
          </div>
          <button type="button" class="btn btn-sm btn-danger" (click)="form.controls.wings.removeAt($index)">Remove wing</button>
        </div>
      }

      <div class="row-between">
        <h3 style="margin:0">Floors</h3>
        <button type="button" class="btn btn-sm" (click)="addFloor()">Add floor</button>
      </div>
      <p class="form-note">
        The code is what the floor is called in addresses: <code>S1</code> for a basement,
        <code>P0</code>, <code>P1</code>, <code>MEZZ</code> for a mezzanine, <code>T</code> for a terrace.
        The level only orders them - a mezzanine between P1 and P2 is 1.5.
      </p>
      @if (form.controls.floors.invalid && form.controls.floors.touched) {
        <div class="field">
          <span class="error">At least one floor is required, every code must be 1-8 uppercase letters or digits, and every floor needs a name.</span>
        </div>
      }

      @for (floor of form.controls.floors.controls; track $index) {
        <div class="card row-card" [formGroup]="floor">
          <div class="cells">
            <div class="field">
              <label [for]="'f-code-' + $index">Code</label>
              <input [id]="'f-code-' + $index" type="text" formControlName="code" placeholder="P1" />
            </div>
            <div class="field">
              <label [for]="'f-level-' + $index">Level</label>
              <input [id]="'f-level-' + $index" type="number" step="0.5" formControlName="level" min="-5" max="99" />
            </div>
            <div class="field">
              <label [for]="'f-name-' + $index">Name</label>
              <input [id]="'f-name-' + $index" type="text" formControlName="name" placeholder="Piso 1" />
            </div>
            <div class="field">
              <label [for]="'f-rows-' + $index">Grid rows</label>
              <input [id]="'f-rows-' + $index" type="number" formControlName="gridRows" min="1" max="60" />
            </div>
            <div class="field">
              <label [for]="'f-cols-' + $index">Grid columns</label>
              <input [id]="'f-cols-' + $index" type="number" formControlName="gridColumns" min="1" max="60" />
            </div>
            <div class="field">
              <label [for]="'f-status-' + $index">Status</label>
              <select [id]="'f-status-' + $index" formControlName="status">
                @for (status of statuses; track status) {
                  <option [value]="status">{{ statusLabels[status] }}</option>
                }
              </select>
            </div>
            <div class="field">
              <label [for]="'f-access-' + $index">Reachable without stairs?</label>
              <select [id]="'f-access-' + $index" formControlName="accessibility">
                @for (value of accessibility; track value) {
                  <option [value]="value">{{ accessibilityLabels[value] }}</option>
                }
              </select>
            </div>
          </div>
          <div class="field">
            <label [for]="'f-note-' + $index">How to get here</label>
            <input [id]="'f-note-' + $index" type="text" formControlName="note" placeholder="Se sube por la escalera exterior" />
          </div>
          <div class="row-between">
            @if (corridorCount(floor.controls.code.value); as count) {
              <span class="form-note">{{ count }} corridor{{ count === 1 ? '' : 's' }} on this floor, kept as they are.</span>
            } @else {
              <span></span>
            }
            <button type="button" class="btn btn-sm btn-danger" (click)="form.controls.floors.removeAt($index)"
                    [disabled]="form.controls.floors.length <= 1">
              Remove floor
            </button>
          </div>
        </div>
      }

      <div class="row">
        <button type="submit" class="btn btn-primary" [disabled]="submitting()">
          {{ submitting() ? 'Saving…' : editing() ? 'Save changes' : 'Create building' }}
        </button>
        <button type="button" class="btn" (click)="cancelled.emit()">Cancel</button>
      </div>
    </form>
  `,
  styles: `
    .form-note {
      margin: 0;
      font-size: 0.75rem;
      color: var(--text-faint);
    }
    .row-card {
      padding: 0.75rem;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .cells {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
      gap: 0.75rem;
    }
  `,
})
export class BuildingFormComponent {
  readonly initial = input<Building | null>(null);
  readonly submitting = input(false);
  readonly submitted = output<BuildingRequest>();
  readonly cancelled = output<void>();

  readonly statuses = FLOOR_STATUSES;
  readonly statusLabels = FLOOR_STATUS_LABELS;
  readonly accessibility = ACCESSIBILITY;
  readonly accessibilityLabels = ACCESSIBILITY_LABELS;

  readonly editing = () => this.initial() !== null;

  form: BuildingForm = this.buildForm(null);

  constructor() {
    effect(() => {
      this.form = this.buildForm(this.initial());
    });
  }

  private buildForm(building: Building | null): BuildingForm {
    return new FormGroup({
      code: new FormControl(building?.code ?? '', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(1), Validators.maxLength(10)],
      }),
      name: new FormControl(building?.name ?? '', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(1), Validators.maxLength(120)],
      }),
      campus: new FormControl(building?.campus ?? '', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(1), Validators.maxLength(120)],
      }),
      description: new FormControl(building?.description ?? '', { nonNullable: true, validators: [Validators.maxLength(500)] }),
      aliases: new FormControl((building?.aliases ?? []).join('\n'), { nonNullable: true }),
      wings: new FormArray((building?.wings ?? []).map((w) => wingGroup(w))),
      floors: new FormArray(
        (building?.floors.length ? building.floors : [{ code: 'P1', level: 1, name: 'Piso 1' }]).map((f) => floorGroup(f)),
        [Validators.required, Validators.minLength(1)],
      ),
    });
  }

  invalid(name: 'code' | 'name' | 'campus'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  addFloor(): void {
    const next = this.form.controls.floors.length + 1;
    this.form.controls.floors.push(floorGroup({ code: `P${next}`, level: next, name: `Piso ${next}` }));
  }

  addWing(): void {
    this.form.controls.wings.push(wingGroup());
  }

  /** How many corridors the stored floor with this code has, so the form can say it keeps them. */
  corridorCount(code: string): number {
    return this.initial()?.floors.find((f) => f.code === code)?.corridors?.length ?? 0;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    // Corridors are carried through untouched, matched by floor code. This form edits a floor's
    // shape; its corridors are drawn in the floor editor, and dropping them here because the form
    // does not show them would silently erase somebody's afternoon of walking a floor.
    const raw = this.form.getRawValue();
    const existing = this.initial()?.floors ?? [];
    this.submitted.emit({
      code: raw.code,
      name: raw.name,
      campus: raw.campus,
      description: raw.description || undefined,
      aliases: raw.aliases.split('\n').map((a) => a.trim()).filter(Boolean),
      wings: raw.wings.map((w) => ({
        code: w.code,
        name: w.name,
        doorSuffix: w.doorSuffix.trim() || null,
        note: w.note.trim() || null,
      })),
      floors: raw.floors.map((floor) => ({
        ...floor,
        level: Number(floor.level),
        note: floor.note.trim() || null,
        corridors: existing.find((f) => f.code === floor.code)?.corridors ?? [],
      })),
    });
  }
}

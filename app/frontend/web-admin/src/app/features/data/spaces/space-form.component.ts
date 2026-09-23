import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import {
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
  type AbstractControl,
  type ValidationErrors,
} from '@angular/forms';
import { ACCESSIBILITY, ACCESSIBILITY_LABELS, type Accessibility, type Building } from '../buildings/building.model';
import { CATEGORY_LABELS, SPACE_CATEGORIES, type Space, type SpaceRequest, type SpaceType } from './space.model';
import { SpacesService } from './spaces.service';

const CODE = /^[A-Za-z0-9][A-Za-z0-9-]*$/;

type SpaceForm = FormGroup<{
  code: FormControl<string>;
  doorCode: FormControl<string>;
  name: FormControl<string>;
  typeCode: FormControl<string>;
  buildingCode: FormControl<string>;
  floorCode: FormControl<string>;
  wing: FormControl<string>;
  gridRow: FormControl<number | null>;
  gridColumn: FormControl<number | null>;
  rowSpan: FormControl<number>;
  colSpan: FormControl<number>;
  accessVia: FormControl<string>;
  accessibility: FormControl<Accessibility | ''>;
  note: FormControl<string>;
  capacity: FormControl<number | null>;
  aliases: FormArray<FormControl<string>>;
}>;

function aliasControl(value = ''): FormControl<string> {
  return new FormControl(value, { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] });
}

/** A space is either placed - row and column - or only inventoried. Half a position is neither. */
function bothOrNeither(group: AbstractControl): ValidationErrors | null {
  const row = group.get('gridRow')?.value;
  const column = group.get('gridColumn')?.value;
  const hasRow = row !== null && row !== undefined && row !== '';
  const hasColumn = column !== null && column !== undefined && column !== '';
  return hasRow === hasColumn ? null : { halfPlaced: true };
}

/**
 * Create/edit form for a space.
 *
 * <p>Building, floor and wing are selects driven by the building itself, so a space cannot name a
 * floor or wing its building does not have. The type comes from the catalogue, grouped by the
 * category clients draw it with.
 *
 * <p>Two codes on purpose. `code` identifies the space and is never shown; `doorCode` is what is
 * printed on the door. For a numbered room they are the same, which is why the first follows the
 * second while it has not been touched. For a stairwell or a bathroom with nothing on its door,
 * the door code stays empty and the app shows the name.
 */
@Component({
  selector: 'app-space-form',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" class="stack">
      <div class="field" [class.invalid]="invalid('buildingCode')">
        <label for="s-building">Building</label>
        <select id="s-building" formControlName="buildingCode" (change)="onBuildingChange()">
          <option value="" disabled>choose a building…</option>
          @for (building of buildings(); track building.code) {
            <option [value]="building.code">{{ building.code }} — {{ building.name }}</option>
          }
        </select>
        @if (invalid('buildingCode')) {
          <span class="error">Required.</span>
        }
      </div>

      <div class="row spread">
        <div class="field" style="flex: 1 1 10rem" [class.invalid]="invalid('floorCode')">
          <label for="s-floor">Floor</label>
          <select id="s-floor" formControlName="floorCode" (change)="onFloorChange()">
            <option value="" disabled>{{ building() ? 'choose a floor…' : 'pick a building first' }}</option>
            @for (floor of floors(); track floor.code) {
              <option [value]="floor.code">{{ floor.code }} — {{ floor.name }}</option>
            }
          </select>
          @if (invalid('floorCode')) {
            <span class="error">Required.</span>
          }
        </div>
        <div class="field" style="flex: 1 1 10rem">
          <label for="s-wing">Wing</label>
          <select id="s-wing" formControlName="wing" [attr.disabled]="wings().length ? null : true">
            <option value="">{{ wings().length ? 'No wing' : 'This building has no wings' }}</option>
            @for (wing of wings(); track wing.code) {
              <option [value]="wing.code">{{ wing.name }}{{ wing.doorSuffix ? ' (' + wing.doorSuffix + ')' : '' }}</option>
            }
          </select>
        </div>
      </div>

      <div class="row spread">
        <div class="field" style="flex: 1 1 10rem">
          <label for="s-door">Number on the door</label>
          <input id="s-door" type="text" formControlName="doorCode" placeholder="503-S" (input)="mirrorDoorCode()" />
          <span class="hint">Exactly as printed. Leave empty when the door has no number.</span>
        </div>
        <div class="field" style="flex: 1 1 10rem" [class.invalid]="invalid('code')">
          <label for="s-code">Internal code</label>
          <input id="s-code" type="text" formControlName="code" placeholder="503-S" />
          @if (invalid('code')) {
            <span class="error">Required: letters, digits and dashes, up to 20.</span>
          } @else {
            <span class="hint">Never shown. Unique in the building - e.g. BANO-P6-C for a bathroom.</span>
          }
        </div>
      </div>

      <div class="field" [class.invalid]="invalid('name')">
        <label for="s-name">Name</label>
        <input id="s-name" type="text" formControlName="name" placeholder="Aula 503" />
        @if (invalid('name')) {
          <span class="error">Required, 1-120 characters.</span>
        }
      </div>

      <div class="field" [class.invalid]="invalid('typeCode')">
        <label for="s-type">Type</label>
        <select id="s-type" formControlName="typeCode">
          <option value="" disabled>choose a type…</option>
          @for (group of typeGroups(); track group.category) {
            <optgroup [label]="group.label">
              @for (type of group.types; track type.code) {
                <option [value]="type.code">{{ type.name }}</option>
              }
            </optgroup>
          }
        </select>
        <span class="hint">Missing one? Add it under Space types; it shows up here right away.</span>
      </div>

      <div class="row spread">
        <div class="field" style="margin-bottom:0; flex: 1 1 6rem">
          <label for="s-row">Grid row</label>
          <input id="s-row" type="number" formControlName="gridRow" min="0" [max]="maxRow()" />
        </div>
        <div class="field" style="margin-bottom:0; flex: 1 1 6rem">
          <label for="s-col">Grid column</label>
          <input id="s-col" type="number" formControlName="gridColumn" min="0" [max]="maxColumn()" />
        </div>
        <div class="field" style="margin-bottom:0; flex: 1 1 5rem">
          <label for="s-rowspan">Rows</label>
          <input id="s-rowspan" type="number" formControlName="rowSpan" min="1" max="60" />
        </div>
        <div class="field" style="margin-bottom:0; flex: 1 1 5rem">
          <label for="s-colspan">Columns</label>
          <input id="s-colspan" type="number" formControlName="colSpan" min="1" max="60" />
        </div>
        <div class="field" style="margin-bottom:0; flex: 1 1 6rem">
          <label for="s-capacity">Capacity</label>
          <input id="s-capacity" type="number" formControlName="capacity" min="0" />
        </div>
      </div>
      <div class="field">
        @if (form.hasError('halfPlaced') && (form.controls.gridRow.touched || form.controls.gridColumn.touched)) {
          <span class="error">Give both a row and a column, or leave both empty.</span>
        }
        @if (floor(); as selected) {
          <span class="hint">
            {{ selected.name }} is {{ selected.gridRows }} x {{ selected.gridColumns }}. Leave row and
            column empty for a space you know is on this floor but have not placed yet.
          </span>
        } @else {
          <span class="hint">Leave row and column empty for a space you know exists but have not placed yet.</span>
        }
      </div>

      <div class="row spread">
        <div class="field" style="flex: 1 1 12rem">
          <label for="s-access-via">Reached via</label>
          <select id="s-access-via" formControlName="accessVia">
            <option value="">Nothing in particular</option>
            @for (option of circulation(); track option.code) {
              <option [value]="option.code">{{ option.name }} ({{ option.floorCode }})</option>
            }
            @if (unknownAccessVia(); as code) {
              <option [value]="code">{{ code }} (not found in this building)</option>
            }
          </select>
          <span class="hint">The lift, stairs or entrance that serves it: "sube por el ascensor central".</span>
        </div>
        <div class="field" style="flex: 1 1 12rem">
          <label for="s-accessibility">Reachable without stairs?</label>
          <select id="s-accessibility" formControlName="accessibility">
            <option value="">Same as the floor ({{ floorAccessibility() }})</option>
            @for (value of accessibility; track value) {
              <option [value]="value">{{ accessibilityLabels[value] }}</option>
            }
          </select>
        </div>
      </div>

      <div class="field">
        <label for="s-note">How to get there</label>
        <input id="s-note" type="text" formControlName="note" placeholder="Solo por la escalera norte, desde el P5" />
        <span class="hint">For what "reached via" cannot say. Up to 300 characters.</span>
      </div>

      <div class="row-between">
        <label style="font-size:0.8125rem; font-weight:600; color:var(--text-muted)">
          Other names <span class="text-faint">(what people actually search for)</span>
        </label>
        <button type="button" class="btn btn-sm" (click)="addAlias()">Add name</button>
      </div>
      @for (alias of form.controls.aliases.controls; track $index) {
        <div class="row">
          <input type="text" [formControl]="alias" placeholder="S-503" />
          <button type="button" class="btn btn-sm btn-danger" (click)="removeAlias($index)">Remove</button>
        </div>
      }

      <div class="row">
        <button type="submit" class="btn btn-primary" [disabled]="submitting()">
          {{ submitting() ? 'Saving…' : editing() ? 'Save changes' : 'Create space' }}
        </button>
        <button type="button" class="btn" (click)="cancelled.emit()">Cancel</button>
      </div>
    </form>
  `,
})
export class SpaceFormComponent {
  private readonly spacesService = inject(SpacesService);

  readonly initial = input<Space | null>(null);
  readonly buildings = input<Building[]>([]);
  readonly types = input<SpaceType[]>([]);
  readonly submitting = input(false);
  readonly submitted = output<SpaceRequest>();
  readonly cancelled = output<void>();

  readonly accessibility = ACCESSIBILITY;
  readonly accessibilityLabels = ACCESSIBILITY_LABELS;
  readonly editing = () => this.initial() !== null;

  /**
   * The selects a form control's value drives, mirrored in signals: a computed reading a form
   * control's value directly would compute once and never again, and the floor list would stay
   * the first building's.
   */
  private readonly buildingCode = signal('');
  private readonly floorCode = signal('');

  readonly building = computed(() => this.buildings().find((b) => b.code === this.buildingCode()) ?? null);
  readonly floors = computed(() => this.building()?.floors ?? []);
  readonly wings = computed(() => this.building()?.wings ?? []);
  readonly floor = computed(() => this.floors().find((f) => f.code === this.floorCode()) ?? null);

  readonly maxRow = computed(() => Math.max(0, (this.floor()?.gridRows ?? 60) - 1));
  readonly maxColumn = computed(() => Math.max(0, (this.floor()?.gridColumns ?? 60) - 1));

  readonly floorAccessibility = computed(() => ACCESSIBILITY_LABELS[this.floor()?.accessibility ?? 'UNKNOWN'].toLowerCase());

  readonly typeGroups = computed(() =>
    SPACE_CATEGORIES.map((category) => ({
      category,
      label: CATEGORY_LABELS[category],
      types: this.types().filter((t) => t.category === category),
    })).filter((group) => group.types.length > 0),
  );

  /**
   * What `accessVia` may point at: the lifts, stairs, ramps and entrances of this building, on any
   * floor. A select, not free text - the value has to match another space's code exactly, and an
   * almost-right code would be refused by the server anyway.
   */
  readonly circulation = signal<Space[]>([]);

  /** A stored accessVia this building no longer lists, kept visible rather than silently dropped. */
  readonly unknownAccessVia = computed(() => {
    const current = this.initial()?.accessVia;
    return current && !this.circulation().some((s) => s.code === current) ? current : null;
  });

  form: SpaceForm = this.buildForm(null);

  constructor() {
    effect(() => {
      const space = this.initial();
      this.form = this.buildForm(space);
      this.buildingCode.set(space?.buildingCode ?? '');
      this.floorCode.set(space?.floorCode ?? '');
      this.loadCirculation(space?.buildingCode ?? '');
    });
  }

  private buildForm(space: Space | null): SpaceForm {
    return new FormGroup(
      {
        code: new FormControl(space?.code ?? '', {
          nonNullable: true,
          validators: [Validators.required, Validators.maxLength(20), Validators.pattern(CODE)],
        }),
        doorCode: new FormControl(space?.doorCode ?? '', { nonNullable: true, validators: [Validators.maxLength(20)] }),
        name: new FormControl(space?.name ?? '', { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] }),
        typeCode: new FormControl(space?.typeCode ?? '', { nonNullable: true, validators: [Validators.required] }),
        buildingCode: new FormControl(space?.buildingCode ?? '', { nonNullable: true, validators: [Validators.required] }),
        floorCode: new FormControl(space?.floorCode ?? '', { nonNullable: true, validators: [Validators.required] }),
        wing: new FormControl(space?.wing ?? '', { nonNullable: true }),
        gridRow: new FormControl<number | null>(space?.gridRow ?? null, { validators: [Validators.min(0)] }),
        gridColumn: new FormControl<number | null>(space?.gridColumn ?? null, { validators: [Validators.min(0)] }),
        rowSpan: new FormControl(space?.rowSpan ?? 1, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(60)] }),
        colSpan: new FormControl(space?.colSpan ?? 1, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(60)] }),
        accessVia: new FormControl(space?.accessVia ?? '', { nonNullable: true }),
        accessibility: new FormControl<Accessibility | ''>(space?.accessibility ?? '', { nonNullable: true }),
        note: new FormControl(space?.note ?? '', { nonNullable: true, validators: [Validators.maxLength(300)] }),
        capacity: new FormControl<number | null>(space?.capacity ?? null, { validators: [Validators.min(0)] }),
        aliases: new FormArray((space?.aliases ?? []).map((a) => aliasControl(a))),
      },
      { validators: bothOrNeither },
    );
  }

  invalid(name: 'code' | 'name' | 'typeCode' | 'buildingCode' | 'floorCode'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  onBuildingChange(): void {
    const code = this.form.controls.buildingCode.value;
    this.buildingCode.set(code);
    // A floor or wing of the previous building means nothing in this one.
    this.form.controls.floorCode.setValue('');
    this.form.controls.wing.setValue('');
    this.form.controls.accessVia.setValue('');
    this.floorCode.set('');
    this.loadCirculation(code);
  }

  onFloorChange(): void {
    this.floorCode.set(this.form.controls.floorCode.value);
  }

  /** While the internal code has not been typed into, it follows the number on the door. */
  mirrorDoorCode(): void {
    const code = this.form.controls.code;
    if (this.editing() || code.dirty) return;
    code.setValue(this.form.controls.doorCode.value.trim().replace(/\s+/g, '-'));
  }

  addAlias(): void {
    this.form.controls.aliases.push(aliasControl());
  }

  removeAlias(index: number): void {
    this.form.controls.aliases.removeAt(index);
  }

  private loadCirculation(buildingCode: string): void {
    this.circulation.set([]);
    if (!buildingCode) return;
    this.spacesService.search({ page: 0, size: 100, buildingCode, category: 'CIRCULATION' }).subscribe({
      next: (page) =>
        this.circulation.set(
          page.content
            .filter((s) => s.code !== this.initial()?.code)
            .sort((a, b) => a.floorLevel - b.floorLevel || a.name.localeCompare(b.name)),
        ),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const placed = raw.gridRow !== null && raw.gridColumn !== null;
    // An empty field means "not set", never "set to empty": the server reads an absent door code
    // as "nothing printed on the door" and an absent accessibility as "the floor's", and an
    // empty string would be stored as a value on both.
    this.submitted.emit({
      code: raw.code.trim(),
      doorCode: raw.doorCode.trim() || null,
      wing: raw.wing || null,
      name: raw.name.trim(),
      typeCode: raw.typeCode,
      buildingCode: raw.buildingCode,
      floorCode: raw.floorCode,
      aliases: raw.aliases.map((a) => a.trim()).filter(Boolean),
      gridRow: placed ? Number(raw.gridRow) : null,
      gridColumn: placed ? Number(raw.gridColumn) : null,
      rowSpan: raw.rowSpan,
      colSpan: raw.colSpan,
      accessVia: raw.accessVia || null,
      accessibility: raw.accessibility || null,
      note: raw.note.trim() || null,
      capacity: raw.capacity ?? undefined,
    });
  }
}

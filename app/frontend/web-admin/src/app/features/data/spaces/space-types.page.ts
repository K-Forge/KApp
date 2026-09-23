import { ChangeDetectionStrategy, Component, ViewChild, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { DataTableComponent } from '../../../shared/ui/data-table/data-table.component';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { CATEGORY_LABELS, SPACE_CATEGORIES, type SpaceCategory, type SpaceType } from './space.model';
import { SpaceTypesService } from './space-types.service';

const TYPE_CODE = /^[A-Z][A-Z0-9_]{1,39}$/;

type TypeForm = FormGroup<{
  code: FormControl<string>;
  name: FormControl<string>;
  category: FormControl<SpaceCategory>;
}>;

/**
 * The catalogue of space types over /api/map/space-types.
 *
 * <p>Types are data so that a new kind of room found while walking a floor - a lactation room, a
 * server room - can be added on the spot, from the iPad, without a release. The category above
 * each type is fixed: it is what clients colour and draw by, so a type they have never heard of
 * still renders as its family.
 */
@Component({
  selector: 'app-space-types-page',
  imports: [ReactiveFormsModule, DataTableComponent, ApiErrorBannerComponent, ModalComponent, PageIntroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-intro
        title="Space types"
        what="What kinds of space the map knows - aula, laboratorio, baño, cuarto de TI - each under the fixed category the apps draw it with."
        [can]="['Add a type the moment you find one', 'Rename a type or move it to another category', 'Delete a type no space uses']"
        note="The code is permanent and never shown; the name is what people read. When you cannot tell what a room is, use Sin identificar and come back to it. A type still used by a space cannot be deleted - the refusal says how many use it."
      >
        <button actions type="button" class="btn btn-primary" (click)="openCreate()">New type</button>
      </app-page-intro>

      <div class="card stack">
        <div class="field" style="margin-bottom: 0; max-width: 20rem">
          <label for="category">Category</label>
          <select id="category" (change)="onCategoryChange($event)">
            <option value="">All categories</option>
            @for (category of categories; track category) {
              <option [value]="category">{{ categoryLabels[category] }}</option>
            }
          </select>
        </div>

        <app-api-error-banner [error]="error()" />

        <app-data-table [loading]="loading()" [empty]="!loading() && !error() && shown().length === 0" emptyMessage="No types in this category yet.">
          <thead>
            <tr>
              <th>Name</th>
              <th>Category</th>
              <th>Code</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (type of shown(); track type.code) {
              <tr>
                <td>{{ type.name }}</td>
                <td><span class="badge badge-neutral">{{ categoryLabels[type.category] }}</span></td>
                <td class="mono text-muted">{{ type.code }}</td>
                <td class="row">
                  <button type="button" class="btn btn-sm" (click)="openEdit(type)">Edit</button>
                  <button type="button" class="btn btn-sm btn-danger" [disabled]="deletingCode() === type.code" (click)="remove(type)">
                    Delete
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </app-data-table>
      </div>
    </div>

    <app-modal #formModal [title]="editing() ? 'Edit type' : 'New type'" (closed)="formError.set(null)">
      <app-api-error-banner [error]="formError()" />
      <form [formGroup]="form" (ngSubmit)="save()" class="stack">
        <div class="field" [class.invalid]="invalid('name')">
          <label for="t-name">Name</label>
          <input id="t-name" type="text" formControlName="name" placeholder="Sala de lactancia" (input)="suggestCode()" />
          @if (invalid('name')) {
            <span class="error">Required, 1-60 characters.</span>
          }
        </div>

        <div class="field">
          <label for="t-category">Category</label>
          <select id="t-category" formControlName="category">
            @for (category of categories; track category) {
              <option [value]="category">{{ categoryLabels[category] }}</option>
            }
          </select>
          <span class="hint">Decides the colour and icon in the apps.</span>
        </div>

        <div class="field" [class.invalid]="invalid('code')">
          <label for="t-code">Code</label>
          <input id="t-code" type="text" formControlName="code" [readonly]="!!editing()" placeholder="LACTATION_ROOM" />
          @if (invalid('code')) {
            <span class="error">Uppercase letters, digits and underscores, starting with a letter; 2-40 characters.</span>
          } @else {
            <span class="hint">{{ editing() ? 'A code cannot change once spaces may use it.' : 'Filled in from the name; change it if you like.' }}</span>
          }
        </div>

        <div class="row">
          <button type="submit" class="btn btn-primary" [disabled]="submitting()">
            {{ submitting() ? 'Saving…' : editing() ? 'Save changes' : 'Create type' }}
          </button>
          <button type="button" class="btn" (click)="formModal.close()">Cancel</button>
        </div>
      </form>
    </app-modal>
  `,
})
export class SpaceTypesPage {
  private readonly service = inject(SpaceTypesService);

  readonly categories = SPACE_CATEGORIES;
  readonly categoryLabels = CATEGORY_LABELS;

  readonly loading = signal(false);
  readonly error = signal<ApiError | null>(null);
  readonly types = signal<SpaceType[]>([]);
  readonly category = signal<SpaceCategory | ''>('');

  /** The server already sorts by category and then name; this only narrows. */
  readonly shown = computed(() => {
    const category = this.category();
    return this.types().filter((t) => !category || t.category === category);
  });

  readonly editing = signal<SpaceType | null>(null);
  readonly submitting = signal(false);
  readonly formError = signal<ApiError | null>(null);
  readonly deletingCode = signal<string | null>(null);

  form: TypeForm = this.buildForm(null);

  @ViewChild('formModal') private formModal?: ModalComponent;

  constructor() {
    this.fetch();
  }

  private fetch(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.list().subscribe({
      next: (types) => {
        this.types.set(types);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }

  private buildForm(type: SpaceType | null): TypeForm {
    return new FormGroup({
      code: new FormControl(type?.code ?? '', { nonNullable: true, validators: [Validators.required, Validators.pattern(TYPE_CODE)] }),
      name: new FormControl(type?.name ?? '', { nonNullable: true, validators: [Validators.required, Validators.maxLength(60)] }),
      category: new FormControl<SpaceCategory>(type?.category ?? (this.category() || 'OTHER'), { nonNullable: true }),
    });
  }

  invalid(name: 'code' | 'name'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  onCategoryChange(event: Event): void {
    this.category.set((event.target as HTMLSelectElement).value as SpaceCategory | '');
  }

  /**
   * Derives the code from the name while the code has not been typed into: "Sala de lactancia"
   * becomes SALA_DE_LACTANCIA. Accents are dropped because the code only allows A-Z.
   */
  suggestCode(): void {
    const code = this.form.controls.code;
    if (this.editing() || code.dirty) return;
    const derived = this.form.controls.name.value
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, '_')
      .replace(/^[^A-Z]+|_+$/g, '')
      .slice(0, 40);
    code.setValue(derived);
  }

  openCreate(): void {
    this.editing.set(null);
    this.form = this.buildForm(null);
    this.formError.set(null);
    this.formModal?.open();
  }

  openEdit(type: SpaceType): void {
    this.editing.set(type);
    this.form = this.buildForm(type);
    this.formError.set(null);
    this.formModal?.open();
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const request: SpaceType = { code: raw.code, name: raw.name.trim(), category: raw.category };
    this.submitting.set(true);
    this.formError.set(null);
    const call = this.editing() ? this.service.update(request) : this.service.create(request);
    call.subscribe({
      next: () => {
        this.submitting.set(false);
        this.formModal?.close();
        this.fetch();
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.formError.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }

  remove(type: SpaceType): void {
    if (!window.confirm(`Delete the type ${type.name}? This cannot be undone.`)) {
      return;
    }
    this.deletingCode.set(type.code);
    this.service.delete(type.code).subscribe({
      next: () => {
        this.deletingCode.set(null);
        this.fetch();
      },
      error: (err: unknown) => {
        this.deletingCode.set(null);
        this.error.set(err instanceof AppHttpError ? err.apiError : null);
      },
    });
  }
}

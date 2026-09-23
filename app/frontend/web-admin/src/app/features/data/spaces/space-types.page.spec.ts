import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { SpaceTypesPage } from './space-types.page';

describe('SpaceTypesPage', () => {
  let page: SpaceTypesPage;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SpaceTypesPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    page = TestBed.createComponent(SpaceTypesPage).componentInstance;
  });

  function suggested(name: string): string {
    page.openCreate();
    page.form.controls.name.setValue(name);
    page.suggestCode();
    return page.form.controls.code.value;
  }

  // A type found on site is named in Spanish, with accents; its code only allows A-Z.
  it('derives a valid code from a Spanish name', () => {
    expect(suggested('Sala de lactancia')).toBe('SALA_DE_LACTANCIA');
    expect(suggested('Baño')).toBe('BANO');
    expect(suggested('Cuarto de TI')).toBe('CUARTO_DE_TI');
    expect(page.form.controls.code.valid).toBe(true);
  });

  it('leaves the code alone once it has been typed into', () => {
    page.openCreate();
    page.form.controls.code.setValue('LACTATION_ROOM');
    page.form.controls.code.markAsDirty();
    page.form.controls.name.setValue('Sala de lactancia');
    page.suggestCode();

    expect(page.form.controls.code.value).toBe('LACTATION_ROOM');
  });

  it('never rewrites the code of a type that already exists', () => {
    page.openEdit({ code: 'RESTROOM', name: 'Baño', category: 'FACILITIES' });
    page.form.controls.name.setValue('Baños');
    page.suggestCode();

    expect(page.form.controls.code.value).toBe('RESTROOM');
  });
});

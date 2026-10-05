import { TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localeEsCo from '@angular/common/locales/es-CO';
import { I18nService, apiText, systemLanguage, t } from './i18n.service';
import { LocalDatePipe } from './local-date.pipe';
import { serverText } from './server-messages';

// As app.config.ts does for the running portal.
registerLocaleData(localeEsCo);

describe('the portal’s language', () => {
  let i18n: I18nService;

  beforeEach(() => {
    i18n = TestBed.inject(I18nService);
  });

  afterEach(() => i18n.set('system'));

  it('follows the system’s language until somebody picks one', () => {
    expect(i18n.preference()).toBe('system');
    expect(i18n.language()).toBe(systemLanguage());
    i18n.set('es');
    expect(i18n.language()).toBe('es');
    i18n.set('en');
    expect(i18n.language()).toBe('en');
  });

  it('says each text in Spanish, moving its data where the Spanish puts it, and keeps English as written', () => {
    i18n.set('es');
    expect(t('Save')).toBe('Guardar');
    expect(t('{n}s ago', { n: 12 })).toBe('hace 12 s');
    expect(t('A text nobody translated')).toBe('A text nobody translated');
    i18n.set('en');
    expect(t('{n}s ago', { n: 12 })).toBe('12s ago');
  });

  it('translates what the services say, with the names and numbers in their message', () => {
    i18n.set('es');
    expect(serverText('Floor P3 of building EC was saved by someone else since you opened it. Reload it to see their changes.')).toBe(
      'Alguien más guardó el piso P3 del edificio EC después de que usted lo abrió. Recárguelo para ver sus cambios.',
    );
    expect(serverText('must not be blank')).toBe('no puede estar vacío');
    expect(serverText('Something only the server knows')).toBe('Something only the server knows');
  });

  it('says what the API contracts say in the portal’s language, as the YAML writes it', () => {
    i18n.set('es');
    expect(apiText('List campuses')).toBe('Listar las sedes');
    expect(apiText('List campuses\n')).toBe('Listar las sedes');
    expect(apiText('A contract text nobody translated')).toBe('A contract text nobody translated');
    expect(apiText(undefined)).toBe('');
    i18n.set('en');
    expect(apiText('List campuses')).toBe('List campuses');
  });

  it('writes dates in the portal’s language', () => {
    const pipe = new LocalDatePipe();
    i18n.set('en');
    expect(pipe.transform('2026-09-29T15:42:00', 'd MMMM y')).toBe('29 September 2026');
    i18n.set('es');
    expect(pipe.transform('2026-09-29T15:42:00', 'd MMMM y')).toBe('29 septiembre 2026');
    expect(pipe.transform(null)).toBe('');
  });
});

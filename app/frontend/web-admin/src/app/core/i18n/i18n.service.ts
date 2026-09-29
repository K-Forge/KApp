import { Injectable, computed, effect, signal } from '@angular/core';
import { ES } from './es';

export type Language = 'es' | 'en';
export type LanguagePreference = 'system' | Language;

const STORAGE_KEY = 'kapp-admin:language';

// One language for the whole portal, held in signals so that everything written with t() - in a
// template, a computed signal, a plain function - follows it when it changes.
const preference = signal<LanguagePreference>(readStoredPreference());
const language = computed<Language>(() => {
  const chosen = preference();
  return chosen === 'system' ? systemLanguage() : chosen;
});

/**
 * The text in the portal's language, with each {name} replaced by its value.
 *
 * <p>The portal is written in English, and that text is the key: `t('Save')` is "Save" in English
 * and whatever es.ts says in Spanish. A text with data in it names the data in braces, so a
 * translation can move it: `t('Block {code}', { code })`. scripts/check-i18n.mjs fails the build
 * when a text the portal shows does not go through here, or has no Spanish.
 */
export function t(text: string, params?: Record<string, unknown>): string {
  let out = language() === 'es' ? (ES[text] ?? text) : text;
  if (params) {
    for (const [name, value] of Object.entries(params)) out = out.split(`{${name}}`).join(String(value ?? ''));
  }
  return out;
}

/** The locale dates and numbers are written in: Colombian Spanish, or English. */
export function locale(): string {
  return language() === 'es' ? 'es-CO' : 'en-US';
}

/**
 * The portal's language: Spanish or English, the system's by default, as the theme is. The choice
 * is kept in this browser.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly preference = preference.asReadonly();
  readonly language = language;
  readonly locale = computed(() => locale());

  constructor() {
    effect(() => {
      document.documentElement.lang = language();
      try {
        localStorage.setItem(STORAGE_KEY, preference());
      } catch {
        // Non-fatal: the choice just won't survive a reload.
      }
    });
  }

  set(next: LanguagePreference): void {
    preference.set(next);
  }

  t(text: string, params?: Record<string, unknown>): string {
    return t(text, params);
  }
}

/** Spanish when the system's first language is any Spanish, English otherwise. */
export function systemLanguage(): Language {
  const first = (typeof navigator !== 'undefined' && (navigator.languages?.[0] ?? navigator.language)) || 'en';
  return first.toLowerCase().startsWith('es') ? 'es' : 'en';
}

function readStoredPreference(): LanguagePreference {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'es' || stored === 'en' || stored === 'system') return stored;
  } catch {
    // Fall through to the default below.
  }
  return 'system';
}

import { t } from './i18n.service';

/**
 * The services answer in English, and some of their messages carry names and numbers: "Floor P3 of
 * building EC was saved by someone else…". A message the portal knows word for word is in es.ts;
 * one with data in it is matched here, and its parts go into the Spanish. Anything else is shown as
 * the service wrote it.
 */
const PATTERNS: [RegExp, string][] = [
  [/^A part cannot start above its top floor, (\d+)$/, /* i18n */ 'A part cannot start above its top floor, {1}'],
  [/^Floor (.+) of building (.+) was saved by someone else since you opened it\. Reload it to see their changes\.$/,
    /* i18n */ 'Floor {1} of building {2} was saved by someone else since you opened it. Reload it to see their changes.'],
  [/^Building (.+) cannot drop a floor or a wing that still has spaces$/,
    /* i18n */ 'Building {1} cannot drop a floor or a wing that still has spaces'],
  [/^Building (.+) still has spaces and cannot be deleted\. Delete its spaces first\.$/,
    /* i18n */ 'Building {1} still has spaces and cannot be deleted. Delete its spaces first.'],
  [/^Space (.+) is how (\d+) other space\(s\) are reached\. Point them elsewhere first\.$/,
    /* i18n */ 'Space {1} is how {2} other space(s) are reached. Point them elsewhere first.'],
  [/^Space type (.+) is still used by at least one space\. Change those spaces first\.$/,
    /* i18n */ 'Space type {1} is still used by at least one space. Change those spaces first.'],
  [/^Space (.+) cannot be placed as sent$/, /* i18n */ 'Space {1} cannot be placed as sent'],
  [/^The floor was not saved: (\d+) problem\(s\)\. Nothing was changed\.$/,
    /* i18n */ 'The floor was not saved: {1} problem(s). Nothing was changed.'],
  [/^The floor was not saved: it removes a space other floors use (.+)$/,
    /* i18n */ 'The floor was not saved: it removes a space other floors use {1}'],
  [/^size must be between (\d+) and (\d+)$/, /* i18n */ 'size must be between {1} and {2}'],
  [/^must be greater than or equal to (.+)$/, /* i18n */ 'must be greater than or equal to {1}'],
  [/^must be less than or equal to (.+)$/, /* i18n */ 'must be less than or equal to {1}'],
  [/^must match "(.+)"$/, /* i18n */ 'must match "{1}"'],
];

/** Messages the services send as they are, with nothing in them to fill. */
export const SERVER_MESSAGES = [
  /* i18n */ 'Authentication required',
  /* i18n */ 'Invalid e-mail or password',
  /* i18n */ 'Validation failed',
  /* i18n */ 'Invalid parameter',
  /* i18n */ 'Missing required parameter',
  /* i18n */ 'E-mail address has not been confirmed',
  /* i18n */ 'Invitation code is invalid, expired or already used',
  /* i18n */ 'Unknown, expired or already used',
  /* i18n */ 'Nothing to update',
  /* i18n */ 'Send at least one field to update',
  /* i18n */ 'The request body must be a JSON object',
  /* i18n */ 'The file has no data rows',
  /* i18n */ 'Pensum failed validation',
  /* i18n */ 'The building’s floors or wings are inconsistent',
  /* i18n */ "The building's floors or wings are inconsistent",
  /* i18n */ "The campus's structures were saved by someone else since you opened them. Reload to see their changes.",
  /* i18n */ "The structures' outlines are not closed rings on the earth",
  /* i18n */ 'The survey was saved from somewhere else since you opened it. Reload to see those distances.',
  /* i18n */ 'Two distances of the survey have the same id',
  /* i18n */ 'Another distance has this id',
  /* i18n */ 'must not be blank',
  /* i18n */ 'must not be null',
  /* i18n */ 'must not be empty',
  /* i18n */ 'must be a well-formed email address',
];

/** A service's message in the portal's language, when the portal knows it. */
export function serverText(message: string | null | undefined): string {
  if (!message) return '';
  for (const [pattern, template] of PATTERNS) {
    const m = pattern.exec(message);
    if (m) return t(template, Object.fromEntries(m.slice(1).map((v, i) => [String(i + 1), v])));
  }
  return t(message);
}

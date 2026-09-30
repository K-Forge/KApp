import type { PlannedDistance, SurveyMeasure } from './survey.model';
import { locale, t } from '../../../core/i18n/i18n.service';

/**
 * The survey sheet's reading and bookkeeping, apart from the screen so it can be tested: what a
 * typed distance adds up to, and which distances still have to reach the server.
 */

/** Longer than this a phone's Measure app drifts: the sheet asks for pieces. */
export const PIECE_METRES = 5;

export interface Reading {
  /** The total in metres; null when nothing was typed or it cannot be read. */
  metres: number | null;
  pieces: number;
  error: string | null;
}

/**
 * What a typed distance adds up to: pieces joined with +, each in metres or with "cm", with a
 * decimal comma or point, as a Spanish keyboard or the Measure app writes it: "4,80 + 3,25",
 * "4.8m", "48 cm".
 */
export function readDistance(text: string | null | undefined): Reading {
  const trimmed = (text ?? '').trim();
  if (!trimmed) return { metres: null, pieces: 0, error: null };
  const parts = trimmed.split('+').map((p) => p.trim().toLowerCase());
  let total = 0;
  for (const part of parts) {
    const match = /^(\d+(?:[.,]\d+)?)\s*(cm|m)?$/.exec(part);
    if (!match) {
      return { metres: null, pieces: parts.length, error: part ? t('Cannot read "{part}"', { part }) : t('A piece is missing') };
    }
    const value = Number(match[1].replace(',', '.'));
    total += match[2] === 'cm' ? value / 100 : value;
  }
  return { metres: Math.round(total * 1000) / 1000, pieces: parts.length, error: null };
}

/**
 * Metres as they were typed: with a decimal comma when the text has one, or with nothing typed, when
 * the portal is in Spanish.
 */
export function formatMetres(metres: number, text?: string | null): string {
  const value = metres.toFixed(2);
  const comma = text ? text.includes(',') : locale().startsWith('es');
  return `${comma ? value.replace('.', ',') : value} m`;
}

/** Nothing typed, noted or named: a distance not worth keeping. */
export function isEmpty(m: SurveyMeasure): boolean {
  return !m.text?.trim() && !m.note?.trim() && !m.label?.trim();
}

/**
 * The survey to save: what the server has, with what was changed here and has not reached it yet
 * laid over it, one distance at a time, and the emptied ones left out.
 */
export function merged(server: readonly SurveyMeasure[], pending: Readonly<Record<string, SurveyMeasure>>): SurveyMeasure[] {
  const out = server.map((m) => pending[m.id] ?? m);
  for (const [id, m] of Object.entries(pending)) {
    if (!server.some((s) => s.id === id)) out.push(m);
  }
  return out.filter((m) => !isEmpty(m)).map(({ updatedAt: _, ...m }) => m);
}

/** The pending changes the server now holds, so they stop being pending. */
export function stillPending(
  pending: Readonly<Record<string, SurveyMeasure>>,
  saved: readonly SurveyMeasure[],
): Record<string, SurveyMeasure> {
  // As the server stores a field: trimmed, and nothing when blank. Compared raw, a trailing space
  // or an empty label never matched what came back, and the sheet saved again forever.
  const kept = (v: string | null | undefined) => (v == null || !v.trim() ? null : v.trim());
  const same = (a: SurveyMeasure, b: SurveyMeasure | undefined) =>
    !!b && kept(a.text) === kept(b.text) && kept(a.note) === kept(b.note) && kept(a.label) === kept(b.label) && !!a.recheck === !!b.recheck;
  const out: Record<string, SurveyMeasure> = {};
  for (const [id, m] of Object.entries(pending)) {
    const there = saved.find((s) => s.id === id);
    if (isEmpty(m) ? !!there : !same(m, there)) out[id] = m;
  }
  return out;
}

/** The whole survey as plain text, in walking order, to paste into a message. */
export function asText(plan: readonly PlannedDistance[], measures: readonly SurveyMeasure[]): string {
  const byId = new Map(measures.map((m) => [m.id, m]));
  const line = (head: string, m: SurveyMeasure | undefined) => {
    const r = readDistance(m?.text);
    const value = r.metres === null ? '—' : formatMetres(r.metres, m?.text) + (r.pieces > 1 ? ` (${m?.text})` : '');
    return `${head}: ${value}${m?.recheck ? ` · ${t('to take again')}` : ''}${m?.note ? ` · ${m.note}` : ''}`;
  };
  const lines = plan.map((d) => line(`${d.n}. [${d.id}] ${d.street} · ${t(d.text)}`, byId.get(d.id)));
  for (const m of measures.filter((x) => !plan.some((d) => d.id === x.id))) {
    lines.push(line(`+ [${m.id}] ${m.label ?? ''}`, m));
  }
  return lines.join('\n');
}

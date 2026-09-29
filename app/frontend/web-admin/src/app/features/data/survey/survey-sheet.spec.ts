import { SURVEY_PLAN } from './survey-plan';
import { asText, formatMetres, merged, readDistance, stillPending } from './survey-sheet';

describe('survey sheet', () => {
  it('adds up the pieces of a distance, as a Spanish keyboard or the Measure app writes them', () => {
    expect(readDistance('4,80 + 3,25')).toEqual({ metres: 8.05, pieces: 2, error: null });
    expect(readDistance(' 4.8m ')).toEqual({ metres: 4.8, pieces: 1, error: null });
    expect(readDistance('48 cm + 1,02')).toEqual({ metres: 1.5, pieces: 2, error: null });
    expect(readDistance('')).toEqual({ metres: null, pieces: 0, error: null });
    expect(readDistance('4.8.1').error).toBe('Cannot read "4.8.1"');
    expect(readDistance('4,80 +').error).toBe('A piece is missing');
    expect(formatMetres(8.05, '4,80 + 3,25')).toBe('8,05 m');
    expect(formatMetres(8.05)).toBe('8.05 m');
  });

  it('saves what the server has with the changes made here laid over it, and drops emptied ones', () => {
    const server = [
      { id: 'bis-01', text: '2,20', metres: 2.2, updatedAt: '2026-09-29T14:00:00Z' },
      { id: 'bis-02', text: '4,10', metres: 4.1 },
    ];
    const pending = {
      'bis-02': { id: 'bis-02', text: '', metres: null },
      'bis-03': { id: 'bis-03', text: '3,3', metres: 3.3, note: 'columns' },
    };
    expect(merged(server, pending)).toEqual([
      { id: 'bis-01', text: '2,20', metres: 2.2 },
      { id: 'bis-03', text: '3,3', metres: 3.3, note: 'columns' },
    ]);
    // Once saved, bis-03 stops being pending; bis-02 is gone from the server, so it is done too.
    expect(stillPending(pending, merged(server, pending))).toEqual({});
    // A change made while the save was on its way stays pending.
    expect(Object.keys(stillPending({ 'bis-01': { id: 'bis-01', text: '2,25' } }, server))).toEqual(['bis-01']);
  });

  it('writes the survey as text in walking order, pieces and notes included', () => {
    const text = asText(SURVEY_PLAN.slice(0, 2), [
      { id: 'bis-02', text: '2,10 + 2,05', metres: 4.15, note: 'tree in the way' },
      { id: 'extra-1', label: 'Gate on Cra 9A', text: '3,10' },
    ]);
    expect(text.split('\n')).toEqual([
      '1. [bis-01] Carrera 9 Bis · North wing, corner with Calle 63: to the Calle 63 curb: —',
      '2. [bis-02] Carrera 9 Bis · North wing, corner with Calle 63: to the Cra 9 Bis curb: 4,15 m (2,10 + 2,05) · tree in the way',
      '+ [extra-1] Gate on Cra 9A: 3,10 m',
    ]);
  });

  it('walks a plan whose distances are numbered in order, each once, and each worth taking', () => {
    expect(SURVEY_PLAN.map((d) => d.n)).toEqual(SURVEY_PLAN.map((_, i) => i + 1));
    expect(new Set(SURVEY_PLAN.map((d) => d.id)).size).toBe(SURVEY_PLAN.length);
    expect(SURVEY_PLAN.every((d) => d.expected > 0 && /^[a-z0-9][a-z0-9-]{0,39}$/.test(d.id))).toBe(true);
  });
});

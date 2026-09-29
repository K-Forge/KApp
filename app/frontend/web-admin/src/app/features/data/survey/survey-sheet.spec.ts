import { toView, type ViewPoint } from '../blocks/block-geometry';
import type { Coordinate } from '../ground/ground.model';
import { SURVEY_FRAME, SURVEY_PLAN } from './survey-plan';
import { asText, formatMetres, merged, readDistance, stillPending } from './survey-sheet';
import type { SurveyMeasure } from './survey.model';

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

  it('stops being pending once saved, though the server trims it and drops what is blank', () => {
    // What the phone sent, and what the server gave back for it.
    const sent = { 'bis-04': { id: 'bis-04', label: '', text: '4,80 + 3,25 ', metres: 8.05, note: ' tree ' } };
    const stored: SurveyMeasure[] = [{ id: 'bis-04', label: null, text: '4,80 + 3,25', metres: 8.05, note: 'tree', updatedAt: '2026-09-29T19:00:00Z' }];
    expect(stillPending(sent, stored)).toEqual({});
    expect(Object.keys(stillPending({ 'bis-04': { id: 'bis-04', text: '4,85' } }, stored))).toEqual(['bis-04']);
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

  it('lays the plan out so that no two lines run on top of each other and no two numbers touch', () => {
    const at = (c: Coordinate) => toView(SURVEY_FRAME, c);
    const drawn = SURVEY_PLAN.map((d) => ({ id: d.id, line: (d.dim ?? [d.a, d.b]).map(at), tag: at(d.tag) }));
    const overlap = ([p1, p2]: ViewPoint[], [q1, q2]: ViewPoint[]) => {
      const len = Math.hypot(p2.x - p1.x, p2.y - p1.y);
      const u = { x: (p2.x - p1.x) / len, y: (p2.y - p1.y) / len };
      const off = (q: ViewPoint) => Math.abs((q.x - p1.x) * u.y - (q.y - p1.y) * u.x);
      const along = (q: ViewPoint) => (q.x - p1.x) * u.x + (q.y - p1.y) * u.y;
      const [s0, s1] = [along(q1), along(q2)].sort((a, b) => a - b);
      return off(q1) < 0.35 && off(q2) < 0.35 ? Math.max(0, Math.min(len, s1) - Math.max(0, s0)) : 0;
    };
    const problems: string[] = [];
    drawn.forEach((p, i) =>
      drawn.slice(i + 1).forEach((q) => {
        if (overlap(p.line, q.line) >= 0.2) problems.push(`${p.id} over ${q.id}`);
        if (Math.hypot(p.tag.x - q.tag.x, p.tag.y - q.tag.y) < 1.5) problems.push(`numbers of ${p.id} and ${q.id}`);
      }),
    );
    expect(problems).toEqual([]);
  });

  it('walks a plan whose distances are numbered in order, each once, and each worth taking', () => {
    expect(SURVEY_PLAN.map((d) => d.n)).toEqual(SURVEY_PLAN.map((_, i) => i + 1));
    expect(new Set(SURVEY_PLAN.map((d) => d.id)).size).toBe(SURVEY_PLAN.length);
    expect(SURVEY_PLAN.every((d) => d.expected > 0 && /^[a-z0-9][a-z0-9-]{0,39}$/.test(d.id))).toBe(true);
  });
});

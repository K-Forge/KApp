import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostListener, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, map, switchMap } from 'rxjs';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { blockOf, middleOf, openRing, pathOf, toView, type ViewPoint } from '../blocks/block-geometry';
import type { Structure } from '../blocks/structure.model';
import { StructuresService } from '../blocks/structures.service';
import type { Building } from '../buildings/building.model';
import { BuildingsService } from '../buildings/buildings.service';
import { areaPath, streetLabel, type Box } from '../ground/ground';
import type { Coordinate, Ground } from '../ground/ground.model';
import { GroundService } from '../ground/ground.service';
import { SURVEY_BLOCK, SURVEY_FRAME, SURVEY_PLAN } from './survey-plan';
import { PIECE_METRES, asText, formatMetres, isEmpty, merged, readDistance, stillPending } from './survey-sheet';
import type { PlannedDistance, SurveyMeasure } from './survey.model';
import { SurveyService } from './survey.service';

type SaveState = 'saved' | 'waiting' | 'saving' | 'offline';

/** Metres round the plan the whole-block view keeps, and the least a zoomed view shows. */
const MARGIN = 6;
const ZOOM_MIN = 20;

/**
 * The survey sheet: the distances to take round the Edificio Central's block with a phone's
 * Measure app, one at a time, on a sketch of the block zoomed to each. What is typed is saved as it
 * is typed, kept on the phone until the server has it, so a walk with no signal loses nothing.
 */
@Component({
  selector: 'app-survey-page',
  imports: [RouterLink, ApiErrorBannerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="survey">
      <header class="head">
        <div>
          <h1>Survey</h1>
          <p class="text-muted small">
            Block {{ block }} · the Edificio Central and what stands round it, from outside ·
            <a [routerLink]="['/data/blocks', block]">reshape by hand</a>
          </p>
        </div>
        <div class="status">
          <span class="badge badge-neutral">{{ done() }} / {{ plan.length }}</span>
          <span class="badge" [class]="stateBadge()">{{ stateText() }}</span>
        </div>
      </header>
      <app-api-error-banner [error]="error()" />
      <details class="how">
        <summary>How to measure</summary>
        <p>Walk the numbers in order, from the corner of Calle 63 and Cra 9 Bis.</p>
        <p><b class="setback-key">Orange</b>: from the wall at street level, straight out from it, to the edge of the street (the curb).</p>
        <p><b class="length-key">Blue</b>: along the wall, corner to corner, or across a door. Drawn beside its wall, with dotted lines back to the corners it runs between.</p>
        <p>
          Longer than {{ pieceMetres }} m, or with something in the way: in pieces. Type one, tap +, type the next. The model's figure is
          only there to catch a slip; what you see on site wins, and anything the sketch gets wrong goes in a note.
        </p>
        <p>Notes are for what the sketch does not show. For example:</p>
        <ul>
          @for (n of noteExamples; track n) {
            <li>{{ n }}</li>
          }
        </ul>
      </details>

      <div class="work">
        <section class="card sketch-card">
          <div class="sketch" #sketchBox>
            @if (drawn(); as v) {
              <svg [attr.viewBox]="viewBox()" preserveAspectRatio="xMidYMid meet" role="img" [attr.aria-label]="'Sketch of block ' + block">
                @for (d of v.roadways; track $index) {
                  <path class="road" [attr.d]="d" />
                }
                @for (d of v.sidewalks; track $index) {
                  <path class="walk" [attr.d]="d" />
                }
                @for (s of v.shapes; track $index) {
                  <path [class]="s.cls" [attr.d]="s.d" />
                }
                @for (s of v.streets; track s.name) {
                  <text class="street" [attr.transform]="'translate(' + s.x + ' ' + s.y + ') rotate(' + s.angle + ')'" [attr.font-size]="11 * k()">{{ s.name }}</text>
                }
                @for (d of v.lines; track d.id) {
                  <g [class]="d.cls" [class.on]="d.id === current().id" (click)="select(d.id)">
                    @if (d.dim; as m) {
                      <line class="ext" [attr.x1]="d.a.x" [attr.y1]="d.a.y" [attr.x2]="m[0].x" [attr.y2]="m[0].y" />
                      <line class="ext" [attr.x1]="d.b.x" [attr.y1]="d.b.y" [attr.x2]="m[1].x" [attr.y2]="m[1].y" />
                      <line [attr.x1]="m[0].x" [attr.y1]="m[0].y" [attr.x2]="m[1].x" [attr.y2]="m[1].y" />
                      <circle class="end" [attr.cx]="m[0].x" [attr.cy]="m[0].y" [attr.r]="2.5 * k()" />
                      <circle class="end" [attr.cx]="m[1].x" [attr.cy]="m[1].y" [attr.r]="2.5 * k()" />
                    } @else {
                      <line [attr.x1]="d.a.x" [attr.y1]="d.a.y" [attr.x2]="d.b.x" [attr.y2]="d.b.y" />
                      <circle class="end" [attr.cx]="d.a.x" [attr.cy]="d.a.y" [attr.r]="3 * k()" />
                    }
                    @if (d.shown) {
                      <circle class="dot" [attr.cx]="d.tag.x" [attr.cy]="d.tag.y" [attr.r]="(d.id === current().id ? 11 : 8.5) * k()" />
                      <text [attr.x]="d.tag.x" [attr.y]="d.tag.y" [attr.font-size]="(d.id === current().id ? 11 : 9) * k()">{{ d.n }}</text>
                    }
                  </g>
                }
              </svg>
            } @else if (loading()) {
              <p class="text-muted small">Loading the block…</p>
            }
          </div>
          <div class="key small">
            <span><i class="swatch setback"></i>wall → street edge: where it stands</span>
            <span><i class="swatch length"></i>along the wall or a door: how wide</span>
          </div>
          <button type="button" class="btn btn-sm zoom" (click)="whole.set(!whole())">{{ whole() ? 'Zoom in' : 'Whole block' }}</button>
        </section>

        <section class="card entry">
          @if (current(); as c) {
            <div class="title">
              <span class="num" [class]="c.kind">{{ c.n ?? '+' }}</span>
              <div>
                <strong>{{ c.text }}</strong>
                <div class="text-muted small">
                  {{ c.street }} · {{ c.kind === 'setback' ? 'wall → curb, straight out' : c.kind === 'length' ? 'along the wall' : 'your own' }}
                  @if (c.expected) {
                    · model {{ c.expected.toFixed(2) }} m{{ c.expected > pieceMetres ? ', in pieces' : '' }}
                  }
                </div>
              </div>
            </div>
            @if (c.kind === 'extra') {
              <input class="label-in" type="text" placeholder="What it is, from where to where" [value]="valueOf(c.id).label ?? ''" (input)="write(c.id, 'label', $event)" />
            }
            <div class="value">
              <input
                #box
                type="text"
                inputmode="decimal"
                autocomplete="off"
                placeholder="4,80"
                [value]="valueOf(c.id).text ?? ''"
                (input)="write(c.id, 'text', $event)"
                [attr.aria-label]="'Distance ' + (c.n ?? '') + ': ' + c.text"
              />
              <button type="button" class="btn" (click)="piece(c.id, box)" title="Another piece" aria-label="Another piece">+</button>
            </div>
            <p class="small reading" [class.bad]="!!reading().error || !!reading().off">{{ readingText() }}</p>
            <div class="steps">
              <button type="button" class="btn" (click)="step(-1)">‹ Back</button>
              <button type="button" class="btn btn-primary" (click)="step(1)">Next ›</button>
            </div>
            <input #note class="note" type="text" placeholder="Note: what the sketch does not show" [value]="valueOf(c.id).note ?? ''" (input)="write(c.id, 'note', $event)" />
            <details class="small examples">
              <summary>Note examples: tap one, then finish it</summary>
              <div class="chips">
                @for (n of noteExamples; track n) {
                  <button type="button" class="chip" (click)="addNote(c.id, n, note)">{{ n }}</button>
                }
              </div>
            </details>
          }
        </section>
      </div>

      <section class="card list">
        @for (g of groups(); track g.street) {
          <h2 class="h">{{ g.street }}</h2>
          <ol>
            @for (d of g.items; track d.id) {
              <li [class.on]="d.id === current().id" (click)="select(d.id, true)">
                <span class="num" [class]="d.kind" [class.done]="d.done">{{ d.n }}</span>
                <span class="what">{{ d.text }}</span>
                <span class="got">{{ d.shown }}</span>
              </li>
            }
          </ol>
        }
        <h2 class="h">Your own</h2>
        <ol>
          @for (x of extras(); track x.id) {
            <li [class.on]="x.id === current().id" (click)="select(x.id, true)">
              <span class="num extra">+</span>
              <span class="what">{{ x.label || 'Not named yet' }}</span>
              <span class="got">{{ x.shown }}</span>
            </li>
          }
        </ol>
        <div class="steps">
          <button type="button" class="btn btn-sm" (click)="addExtra()">+ Something the plan missed</button>
          <button type="button" class="btn btn-sm" (click)="showText.set(!showText())">{{ showText() ? 'Hide' : 'As text' }}</button>
        </div>
        @if (showText()) {
          <textarea class="as-text" readonly rows="10" [value]="text()" (focus)="$any($event.target).select()"></textarea>
        }
      </section>
    </div>
  `,
  styles: `
    .survey { display: grid; gap: 0.9rem; }
    .head { display: flex; flex-wrap: wrap; gap: 0.5rem 1rem; justify-content: space-between; align-items: flex-start; }
    h1 { margin: 0; }
    .status, .steps, .value { display: flex; gap: 0.5rem; flex-wrap: wrap; }
    .how { font-size: 0.875rem; }
    .how p { margin: 0.35rem 0; }
    .setback-key { color: #e8590c; }
    .length-key { color: #1c7ed6; }
    .work { display: grid; gap: 0.9rem; }
    @media (min-width: 900px) { .work { grid-template-columns: 3fr 2fr; align-items: start; } }
    .sketch-card { position: relative; padding: 0.4rem; }
    .sketch { height: 46vh; min-height: 260px; }
    .sketch svg { width: 100%; height: 100%; display: block; }
    .zoom { position: absolute; right: 0.6rem; bottom: 3.4rem; }
    .key { display: flex; flex-wrap: wrap; gap: 0.2rem 1rem; padding: 0.4rem 0.3rem 0.1rem; color: var(--text-muted); }
    .swatch { display: inline-block; width: 1.1rem; height: 3px; margin-right: 0.4rem; vertical-align: middle; background: var(--c); }
    .chips { display: flex; flex-wrap: wrap; gap: 0.3rem; margin-top: 0.4rem; }
    .chip { border: 1px solid var(--border); background: none; color: var(--text-muted); border-radius: 999px; padding: 0.15rem 0.55rem; font-size: 0.75rem; cursor: pointer; }
    svg * { vector-effect: non-scaling-stroke; }
    .road { fill: color-mix(in srgb, var(--text) 12%, transparent); }
    .walk { fill: #d9ccae; fill-opacity: 0.55; stroke: #b9ab8c; stroke-width: 0.6; }
    .built { fill: color-mix(in srgb, var(--primary) 22%, transparent); stroke: var(--text-muted); stroke-width: 0.8; }
    .other { fill: color-mix(in srgb, var(--text) 14%, transparent); stroke: var(--text-muted); stroke-width: 0.8; }
    .open { fill: none; stroke: var(--text-muted); stroke-width: 0.8; stroke-dasharray: 3 2; }
    .street, text { text-anchor: middle; dominant-baseline: central; pointer-events: none; }
    .street { fill: var(--text-muted); font-weight: 600; }
    g { cursor: pointer; }
    g line { stroke-width: 2; stroke: currentColor; }
    g line.ext { stroke-width: 1; stroke-dasharray: 3 3; }
    g .end, g .dot { fill: currentColor; }
    g text { fill: #fff; font-weight: 700; }
    .setback { color: #e8590c; --c: #e8590c; }
    .length { color: #1c7ed6; --c: #1c7ed6; }
    .done { color: #2f9e44; --c: #2f9e44; }
    g.on line { stroke-width: 4; }
    g.on .dot { stroke: var(--bg-elevated); stroke-width: 2.5; }
    .title { display: flex; gap: 0.6rem; align-items: flex-start; }
    .num { flex: none; display: inline-grid; place-items: center; min-width: 1.7rem; height: 1.7rem; border-radius: 999px; background: var(--c, #868e96); color: #fff; font-size: 0.75rem; font-weight: 700; }
    .entry input { width: 100%; }
    .value { margin-top: 0.7rem; flex-wrap: nowrap; }
    .value input { font-size: 1.4rem; }
    .label-in { margin-top: 0.6rem; }
    .reading { min-height: 1.2em; margin: 0.35rem 0; color: var(--text-muted); }
    .reading.bad { color: var(--danger, #c92a2a); }
    .steps { margin: 0.6rem 0; justify-content: space-between; }
    .examples { margin-top: 0.4rem; color: var(--text-muted); }
    .list ol { list-style: none; margin: 0 0 0.6rem; padding: 0; }
    .list li { display: flex; gap: 0.6rem; align-items: center; padding: 0.3rem 0.2rem; border-radius: 6px; cursor: pointer; font-size: 0.875rem; }
    .list li.on { background: color-mix(in srgb, var(--primary) 14%, transparent); }
    .what { flex: 1; }
    .got { font-variant-numeric: tabular-nums; }
    .as-text { width: 100%; font-family: ui-monospace, monospace; font-size: 0.75rem; }
  `,
})
export class SurveyPage {
  private readonly buildingsService = inject(BuildingsService);
  private readonly grounds = inject(GroundService);
  private readonly structuresService = inject(StructuresService);
  private readonly surveyService = inject(SurveyService);
  private readonly sketchBox = viewChild<ElementRef<HTMLElement>>('sketchBox');

  readonly plan = SURVEY_PLAN;
  readonly block = SURVEY_BLOCK;
  readonly pieceMetres = PIECE_METRES;
  /** What goes in a note, to tap in and finish: what the sketch cannot show. */
  readonly noteExamples = [
    'Columns in front: measured to the wall behind',
    'In pieces round a tree / post / car',
    'The wall steps in here, … m',
    'The floors above come out … m',
    'The door is … m further on',
    'No door here',
    'To a fence or railing, not a wall',
    'The curb is a driveway ramp here',
    'Could not reach it: estimated',
  ];

  readonly loading = signal(true);
  readonly error = signal<ApiError | null>(null);
  readonly campus = signal('');
  private readonly buildings = signal<Building[]>([]);
  private readonly structures = signal<Structure[]>([]);
  private readonly ground = signal<Ground | null>(null);
  /** What the server holds, at `version`. */
  readonly server = signal<SurveyMeasure[]>([]);
  private version = 0;
  /** What was changed here and has not reached the server yet; also kept in the browser. */
  readonly pending = signal<Record<string, SurveyMeasure>>({});
  readonly saveState = signal<SaveState>('saved');
  readonly currentId = signal(SURVEY_PLAN[0].id);
  readonly whole = signal(false);
  readonly showText = signal(false);
  /** The sketch's size on screen, in pixels, to keep its numbers readable at every zoom. */
  private readonly size = signal({ w: 360, h: 300 });
  private timer: ReturnType<typeof setTimeout> | undefined;
  private inFlight = false;

  /** Every distance as it stands here: the server's, with this device's changes laid over. */
  readonly values = computed(() => {
    const out = new Map(this.server().map((m) => [m.id, m] as const));
    for (const [id, m] of Object.entries(this.pending())) out.set(id, m);
    return out;
  });

  readonly done = computed(() => this.plan.filter((d) => readDistance(this.values().get(d.id)?.text).metres !== null).length);

  readonly extras = computed(() =>
    [...this.values().values()]
      .filter((m) => !this.plan.some((d) => d.id === m.id) && (!isEmpty(m) || this.currentId() === m.id))
      .map((m) => ({ id: m.id, label: m.label ?? '', shown: this.shown(m) })),
  );

  readonly current = computed(() => {
    const id = this.currentId();
    const planned = this.plan.find((d) => d.id === id);
    if (planned) return { ...planned, kind: planned.kind as PlannedDistance['kind'] | 'extra' };
    const label = this.values().get(id)?.label;
    return { id, n: null, street: 'Something the plan missed', kind: 'extra' as const, text: label || 'Not named yet', expected: 0 };
  });

  readonly reading = computed(() => {
    const c = this.current();
    const r = readDistance(this.values().get(c.id)?.text);
    const off = r.metres !== null && c.expected > 0 && Math.abs(r.metres - c.expected) > Math.max(0.5, c.expected * 0.2);
    return { ...r, off };
  });

  readonly readingText = computed(() => {
    const c = this.current();
    const r = this.reading();
    if (r.error) return r.error;
    if (r.metres === null) return c.kind === 'setback' ? 'Metres from the wall to the curb.' : 'Metres.';
    const text = this.values().get(c.id)?.text;
    const total = r.pieces > 1 ? `= ${formatMetres(r.metres, text)} in ${r.pieces} pieces` : formatMetres(r.metres, text);
    if (!c.expected) return total;
    const delta = r.metres - c.expected;
    return r.off
      ? `${total} · the model says ${formatMetres(c.expected, text)}: check it, then note what differs`
      : `${total} · ${delta >= 0 ? '+' : '−'}${formatMetres(Math.abs(delta), text)} from the model`;
  });

  readonly groups = computed(() => {
    const out: { street: string; items: (PlannedDistance & { done: boolean; shown: string })[] }[] = [];
    for (const d of this.plan) {
      let g = out.find((x) => x.street === d.street);
      if (!g) out.push((g = { street: d.street, items: [] }));
      const m = this.values().get(d.id);
      g.items.push({ ...d, done: readDistance(m?.text).metres !== null, shown: m ? this.shown(m) : '' });
    }
    return out;
  });

  readonly text = computed(() => asText(this.plan, merged(this.server(), this.pending())));

  /** The block on the sketch's screen: Calle 63 up, as the block editor turns it. */
  readonly view = computed(() => {
    const ground = this.ground();
    if (!ground) return null;
    const at = (c: Coordinate) => toView(SURVEY_FRAME, c);
    const planned = this.plan.map((d) => ({ d, a: at(d.a), b: at(d.b) }));
    const all = planned.flatMap((p) => [p.a, p.b]);
    const box: Box = {
      x: Math.min(...all.map((p) => p.x)) - MARGIN,
      y: Math.min(...all.map((p) => p.y)) - MARGIN,
      width: Math.max(...all.map((p) => p.x)) - Math.min(...all.map((p) => p.x)) + 2 * MARGIN,
      height: Math.max(...all.map((p) => p.y)) - Math.min(...all.map((p) => p.y)) + 2 * MARGIN,
    };
    const near = (r: Coordinate[]) =>
      r.some((c) => {
        const p = at(c);
        return p.x > box.x - 30 && p.x < box.x + box.width + 30 && p.y > box.y - 30 && p.y < box.y + box.height + 30;
      });
    const area = (rings: Coordinate[][]) => rings.filter(near).map((r) => areaPath(r.map(at)));
    const shape = (ring: Coordinate[], floors: number, ours: boolean) => ({
      cls: floors > 0 ? (ours ? 'built' : 'other') : 'open',
      d: pathOf(openRing(ring).map(at)),
    });
    const shapes = [
      ...this.buildings()
        .filter((b) => (b.footprint ?? []).some((p) => blockOf(p.lot) === SURVEY_BLOCK))
        .flatMap((b) => (b.footprint ?? []).map((p) => shape(p.ring, p.floors, true))),
      ...this.structures().filter((s) => near(s.ring)).map((s) => shape(s.ring, s.floors, false)),
    ];
    // Where each line and number goes was laid out by scripts/survey-plan.py, clear of the others.
    const lines = planned.map(({ d, a, b }) => {
      const done = readDistance(this.values().get(d.id)?.text).metres !== null;
      const dim = d.dim ? ([at(d.dim[0]), at(d.dim[1])] as const) : null;
      return { id: d.id, n: d.n, a, b, dim, tag: at(d.tag), cls: done ? 'done' : d.kind };
    });
    const streets: { name: string; x: number; y: number; angle: number }[] = [];
    for (const street of ground.streets) {
      const label = streetLabel(street.path.map(at), box, 40);
      if (label && !streets.some((s) => s.name === street.name)) streets.push({ name: street.name, ...label });
    }
    return { box, roadways: area(ground.roadways), sidewalks: area(ground.sidewalks), shapes, lines, streets };
  });

  /**
   * The lines as drawn, with the numbers that fit: zoomed in, all of them; the whole block, only
   * those that do not touch a number already shown, the current one first.
   */
  readonly drawn = computed(() => {
    const v = this.view();
    if (!v) return null;
    const room = 19 * this.k();
    const kept: ViewPoint[] = [];
    const current = this.currentId();
    const order = [...v.lines].sort((x, y) => (x.id === current ? -1 : y.id === current ? 1 : x.n - y.n));
    const shown = new Set<string>();
    for (const l of order) {
      if (!this.whole() || kept.every((t) => Math.hypot(t.x - l.tag.x, t.y - l.tag.y) >= room)) {
        kept.push(l.tag);
        shown.add(l.id);
      }
    }
    return { ...v, lines: v.lines.map((l) => ({ ...l, shown: shown.has(l.id) })) };
  });

  /** The whole block, or a window round the current distance. */
  readonly viewBox = computed(() => {
    const v = this.view();
    if (!v) return '0 0 1 1';
    const line = v.lines.find((l) => l.id === this.currentId());
    if (this.whole() || !line) return `${v.box.x} ${v.box.y} ${v.box.width} ${v.box.height}`;
    const pts = [line.a, line.b, line.tag, ...(line.dim ?? [])];
    const xs = pts.map((p) => p.x);
    const ys = pts.map((p) => p.y);
    const size = Math.max(ZOOM_MIN, Math.max(...xs) - Math.min(...xs) + 10, Math.max(...ys) - Math.min(...ys) + 10);
    const cx = (Math.max(...xs) + Math.min(...xs)) / 2;
    const cy = (Math.max(...ys) + Math.min(...ys)) / 2;
    return `${cx - size / 2} ${cy - size / 2} ${size} ${size}`;
  });

  /** Metres per pixel on the sketch: its numbers and labels are sized in pixels. */
  readonly k = computed(() => {
    const [, , w, h] = this.viewBox().split(' ').map(Number);
    const { w: pw, h: ph } = this.size();
    return 1 / Math.max(1e-6, Math.min(pw / w, ph / h));
  });

  readonly stateText = computed(
    () =>
      ({
        saved: 'Saved',
        waiting: 'Saving soon',
        saving: 'Saving…',
        offline: 'Kept on this device, not saved yet',
      })[this.saveState()],
  );

  readonly stateBadge = computed(() => (this.saveState() === 'offline' ? 'badge badge-warning' : this.saveState() === 'saved' ? 'badge badge-success' : 'badge badge-neutral'));

  constructor() {
    this.load();
    const destroyRef = inject(DestroyRef);
    destroyRef.onDestroy(() => clearTimeout(this.timer));
    afterNextRender(() => {
      const el = this.sketchBox()?.nativeElement;
      if (!el || typeof ResizeObserver === 'undefined') return;
      // A sketch that is not on screen measures 0: keep the last size it had.
      const observer = new ResizeObserver(([entry]) => {
        const { width: w, height: h } = entry.contentRect;
        if (w > 0 && h > 0) this.size.set({ w, h });
      });
      observer.observe(el);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  valueOf(id: string): SurveyMeasure {
    return this.values().get(id) ?? { id };
  }

  select(id: string, scrollUp = false): void {
    this.currentId.set(id);
    this.whole.set(false);
    if (scrollUp) this.sketchBox()?.nativeElement.scrollIntoView?.({ block: 'start', behavior: 'smooth' });
  }

  /** The next or previous distance of the walk; Next skips the ones already taken. */
  step(by: 1 | -1): void {
    const order = [...this.plan.map((d) => d.id), ...this.extras().map((x) => x.id)];
    const at = order.indexOf(this.currentId());
    for (let i = 1; i <= order.length; i++) {
      const id = order[(at + by * i + order.length * i) % order.length];
      if (by < 0 || readDistance(this.values().get(id)?.text).metres === null) {
        this.select(id);
        return;
      }
    }
    this.select(order[(at + by + order.length) % order.length]);
  }

  /** An example added to the note, to finish by hand: the cursor goes to its "…". */
  addNote(id: string, example: string, box: HTMLInputElement): void {
    const note = (this.valueOf(id).note ?? '').trim();
    const next = note ? `${note}; ${example}` : example;
    this.change(id, { note: next });
    box.value = next;
    box.focus();
    const at = next.lastIndexOf('…');
    if (at >= 0) box.setSelectionRange(at, at + 1);
  }

  write(id: string, field: 'text' | 'note' | 'label', event: Event): void {
    this.change(id, { [field]: (event.target as HTMLInputElement).value });
  }

  /** Another piece: " + " after what is typed, and the cursor back in the box. */
  piece(id: string, box: HTMLInputElement): void {
    const text = (this.valueOf(id).text ?? '').trim();
    if (text && !text.endsWith('+')) this.change(id, { text: `${text} + ` });
    box.value = this.valueOf(id).text ?? '';
    box.focus();
  }

  addExtra(): void {
    const used = [...this.values().keys()].filter((id) => id.startsWith('extra-')).map((id) => Number(id.slice(6)) || 0);
    const id = `extra-${Math.max(0, ...used) + 1}`;
    this.pending.update((p) => ({ ...p, [id]: { id, label: '', text: '' } }));
    this.select(id);
  }

  private change(id: string, patch: Partial<SurveyMeasure>): void {
    const next: SurveyMeasure = { ...this.valueOf(id), ...patch, id };
    delete next.updatedAt;
    if ('text' in patch) next.metres = readDistance(next.text).metres;
    this.pending.update((p) => ({ ...p, [id]: next }));
    this.keep();
    this.schedule(1200);
  }

  private schedule(ms: number): void {
    clearTimeout(this.timer);
    if (this.saveState() !== 'offline') this.saveState.set('waiting');
    this.timer = setTimeout(() => this.save(), ms);
  }

  /** Everything, with the version read: the server keeps when each distance was taken. */
  save(): void {
    if (this.inFlight) return this.schedule(800);
    const pending = this.pending();
    if (!Object.keys(pending).length) {
      this.saveState.set('saved');
      return;
    }
    this.inFlight = true;
    this.saveState.set('saving');
    this.surveyService.save(this.campus(), { version: this.version, measures: merged(this.server(), pending) }).subscribe({
      next: (survey) => {
        this.inFlight = false;
        this.version = survey.version;
        this.server.set(survey.measures);
        this.pending.set(stillPending(this.pending(), survey.measures));
        this.keep();
        if (Object.keys(this.pending()).length) this.schedule(800);
        else this.saveState.set('saved');
      },
      error: (err: unknown) => {
        this.inFlight = false;
        if (err instanceof AppHttpError && err.apiError?.status === 409) {
          // Saved from another device since: read it, lay this one's changes over, save again.
          this.surveyService.forCampus(this.campus()).subscribe({
            next: (survey) => {
              this.version = survey.version;
              this.server.set(survey.measures);
              this.schedule(0);
            },
            error: () => this.offline(),
          });
          return;
        }
        this.offline();
      },
    });
  }

  @HostListener('window:online')
  onOnline(): void {
    if (Object.keys(this.pending()).length) this.schedule(0);
  }

  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(event: BeforeUnloadEvent): void {
    if (Object.keys(this.pending()).length && !this.kept) event.preventDefault();
  }

  private offline(): void {
    this.saveState.set('offline');
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.save(), 15000);
  }

  private shown(m: SurveyMeasure): string {
    const r = readDistance(m.text);
    return r.metres === null ? (m.note ? 'note' : '') : formatMetres(r.metres, m.text);
  }

  private get storageKey(): string {
    return `kapp-admin:survey-pending:${this.campus().toLowerCase()}`;
  }

  /** Whether the pending changes are in the browser too, where a reload finds them. */
  private kept = false;

  private keep(): void {
    try {
      localStorage.setItem(this.storageKey, JSON.stringify(this.pending()));
      this.kept = true;
    } catch {
      this.kept = false;
    }
  }

  private restore(): Record<string, SurveyMeasure> {
    try {
      const raw = localStorage.getItem(this.storageKey);
      const parsed = raw ? (JSON.parse(raw) as Record<string, SurveyMeasure>) : {};
      return parsed && typeof parsed === 'object' ? parsed : {};
    } catch {
      return {};
    }
  }

  private load(): void {
    this.buildingsService
      .list()
      .pipe(
        switchMap((all) => {
          const campus = all.find((b) => (b.footprint ?? []).some((p) => blockOf(p.lot) === SURVEY_BLOCK))?.campus ?? all[0]?.campus ?? '';
          return forkJoin({
            ground: this.grounds.forCampus(campus),
            structures: this.structuresService.forCampus(campus),
            survey: this.surveyService.forCampus(campus),
          }).pipe(map((r) => ({ ...r, campus, onCampus: all.filter((b) => b.campus === campus) })));
        }),
      )
      .subscribe({
        next: ({ ground, structures, survey, campus, onCampus }) => {
          this.campus.set(campus);
          this.buildings.set(onCampus);
          this.ground.set(ground);
          this.structures.set(structures.structures);
          this.version = survey.version;
          this.server.set(survey.measures);
          this.pending.set(stillPending(this.restore(), survey.measures));
          this.loading.set(false);
          const first = this.plan.find((d) => readDistance(this.values().get(d.id)?.text).metres === null);
          if (first) this.currentId.set(first.id);
          if (Object.keys(this.pending()).length) this.schedule(0);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          this.error.set(err instanceof AppHttpError ? err.apiError : null);
        },
      });
  }
}

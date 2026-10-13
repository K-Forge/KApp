import { PageIntroComponent } from '../../../shared/ui/page-intro/page-intro.component';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostListener, Injector, afterNextRender, afterRenderEffect, computed, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, map, switchMap } from 'rxjs';
import { AppHttpError } from '../../../core/http/api-http-error';
import type { ApiError } from '../../../core/http/api-error.model';
import { ApiErrorBannerComponent } from '../../../shared/ui/api-error-banner/api-error-banner.component';
import { PinchZoomDirective, type ZoomStep } from '../../../shared/ui/pinch-zoom/pinch-zoom.directive';
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
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { t } from '../../../core/i18n/i18n.service';

type SaveState = 'saved' | 'waiting' | 'saving' | 'offline';
type DistanceState = 'todo' | 'done' | 'review';

/** Metres round the plan the whole-block view keeps, the least a distance is framed in, and the closest a pinch goes. */
const MARGIN = 6;
const ZOOM_MIN = 20;
const CLOSEST = 4;

/**
 * The survey sheet: the distances to take round the Edificio Central's block with a phone's
 * Measure app, one at a time, on a sketch of the block zoomed to each. What is typed is saved as it
 * is typed, kept on the phone until the server has it, so a walk with no signal loses nothing.
 */
@Component({
  selector: 'app-survey-page',
  imports: [TranslatePipe, RouterLink, ApiErrorBannerComponent, PinchZoomDirective, PageIntroComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="survey">
      <app-page-intro
        [title]="'Survey' | t"
        [what]="'The block from outside, measured on site: each number is one distance to take, with a tape or the iPhone’s Measure.' | t"
        [can]="[('Take the distances in order and type each one' | t), ('Mark one to take again' | t), ('Zoom the sketch with a pinch, or Ctrl/⌘ and the wheel' | t)]"
        [note]="'It saves as you type. What you measure on site wins over the model’s figure.' | t"
      >
        <p lede class="text-muted lede">
          {{ 'Block {block} · the Edificio Central and what stands round it, from outside ·' | t: { block: block } }}
          <a [routerLink]="['/data/blocks', block]">{{ 'reshape by hand' | t }}</a>
        </p>
        <div actions class="status">
          <span class="badge badge-neutral">{{ done() }} / {{ plan.length }}</span>
          @if (toReview()) {
            <span class="badge review-badge">{{ toReview() === 1 ? ('1 to take again' | t) : ('{n} to take again' | t: { n: toReview() }) }}</span>
          }
          <span class="badge" [class]="stateBadge()">{{ stateText() }}</span>
        </div>
      <details help class="help how">
        <summary>
          <svg class="help-icon" viewBox="0 0 16 16" width="15" height="15" aria-hidden="true">
            <path d="M2 11.5 11.5 2l2.5 2.5L4.5 14H2z" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round" />
            <path d="M9.5 4 12 6.5" stroke="currentColor" stroke-width="1.4" />
          </svg>
          {{ 'How to measure' | t }}
          <svg class="help-chevron" viewBox="0 0 12 12" width="11" height="11" aria-hidden="true">
            <path d="M2.5 4.5 6 8l3.5-3.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </summary>
        <div class="help-body">
          <p>{{ 'Walk the numbers in order, from the corner of Calle 63 and Cra 9 Bis.' | t }}</p>
          <ul class="keys">
            <li><b class="setback-key">{{ 'Orange' | t }}</b> {{ 'wall to curb, straight out, at street level' | t }}</li>
            <li><b class="length-key">{{ 'Blue' | t }}</b> {{ 'along the wall, corner to corner, or across a door' | t }}</li>
            <li><b class="review-key">{{ 'Violet' | t }}</b> {{ 'to take again: the earlier figure shows under the box' | t }}</li>
          </ul>
          <p>{{ 'Over {pieceMetres} m, or with something in the way: in pieces, with +. The model’s figure only catches a slip; what you see on site wins.' | t: { pieceMetres: pieceMetres } }}</p>
          <p>{{ 'Notes are for what the sketch does not show, for example:' | t }}</p>
          <ul>
            @for (n of noteExamples; track n) {
              <li>{{ n | t }}</li>
            }
          </ul>
        </div>
      </details>
      </app-page-intro>
      <app-api-error-banner [error]="error()" />

      <div class="work">
        <section class="card sketch-card">
          <div class="sketch" #sketchBox (appPinchZoom)="zoomAt($event)" (wheel)="onWheel($event)">
            @if (drawn(); as v) {
              <svg
                [attr.viewBox]="viewBox()"
                preserveAspectRatio="xMidYMid meet"
                role="img"
                [attr.aria-label]="('Sketch of block ' | t) + block"
                [class.zoomed]="zoomed()"
                (pointerdown)="onDown($event)"
                (pointermove)="onMove($event)"
                (pointerup)="onUp($event)"
                (pointercancel)="onUp($event)"
                (click.capture)="onClickCapture($event)"
              >
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
              <p class="text-muted small">{{ 'Loading the block…' | t }}</p>
            }
          </div>
          <div class="key small">
            <span><i class="swatch setback"></i>{{ 'wall → street edge: where it stands' | t }}</span>
            <span><i class="swatch length"></i>{{ 'along the wall or a door: how wide' | t }}</span>
            <span><i class="swatch done"></i>{{ 'taken' | t }}</span>
            <span><i class="swatch review"></i>{{ 'to take again' | t }}</span>
            <span class="gesture-hint">{{ 'Pinch, or Ctrl/⌘ and the wheel, to zoom; drag to move' | t }}</span>
          </div>
        </section>

        <section class="card entry" #entryBox>
          @if (current(); as c) {
            <div class="title">
              <span class="num" [class]="c.kind">{{ c.n ?? '+' }}</span>
              <div>
                <strong>{{ c.text | t }}</strong>
                <div class="text-muted small">
                  {{ c.street | t }} · {{ c.kind === 'setback' ? ('wall → curb, straight out' | t) : c.kind === 'length' ? ('along the wall' | t) : ('your own' | t) }}
                  @if (c.expected) {
                    {{ '· model {value}{value2}' | t: { value: metres(c.expected), value2: c.expected > pieceMetres ? (', in pieces' | t) : '' } }}
                  }
                </div>
              </div>
            </div>
            @if (c.hint) {
              <p class="small" style="margin: 0.5rem 0 0; color: var(--text-muted)">{{ c.hint | t }}</p>
            }
            @if (c.kind === 'extra') {
              <input class="label-in" type="text" [placeholder]="'What it is, from where to where' | t" [value]="valueOf(c.id).label ?? ''" (input)="write(c.id, 'label', $event)" />
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
                [attr.aria-label]="'Distance {n}: {text}' | t: { n: c.n ?? '', text: (c.text | t) }"
              />
              <button type="button" class="btn" (click)="piece(c.id, box)" [title]="'Another piece' | t" [attr.aria-label]="'Another piece' | t">+</button>
            </div>
            <p class="small reading" [class.bad]="!!reading().error || !!reading().off">{{ readingText() }}</p>
            @if (valueOf(c.id).recheck || before()[c.id]) {
              <p class="small review-note">{{ 'To take again. Taken before: {value}' | t: { value: before()[c.id] || shownOf(c.id) || '—' } }}</p>
            }
            <div class="steps">
              <button type="button" class="btn" (click)="step(-1)">{{ '‹ Back' | t }}</button>
              <button type="button" class="btn btn-primary" (click)="step(1)">{{ 'Next ›' | t }}</button>
              @if (c.kind !== 'extra' && stateOf(c.id) !== 'todo') {
                <button type="button" class="btn btn-sm recheck-btn" (click)="toggleRecheck(c.id)">
                  {{ valueOf(c.id).recheck ? ('It is right' | t) : ('Retake' | t) }}
                </button>
              }
            </div>
            <input #note class="note" type="text" [placeholder]="'Note' | t" [value]="valueOf(c.id).note ?? ''" (input)="write(c.id, 'note', $event)" />
            <details class="small examples">
              <summary>{{ 'Note examples: tap one, then finish it' | t }}</summary>
              <div class="chips">
                @for (n of noteExamples; track n) {
                  <button type="button" class="chip" (click)="addNote(c.id, n, note)">{{ n | t }}</button>
                }
              </div>
            </details>
            @if (upNext(); as u) {
              <button type="button" class="next" (click)="select(u.id)">
                <span class="text-muted">{{ 'Then' | t }}</span>
                <span class="title">
                  <span class="num" [class]="u.kind">{{ u.n }}</span>
                  <span>{{ u.text | t }} <span class="text-muted">· {{ u.street | t }}</span></span>
                </span>
              </button>
            }
          }
        </section>

        <section class="card list" #listBox>
        @for (g of groups(); track g.street) {
          <h2 class="h">{{ g.street | t }}</h2>
          <ol>
            @for (d of g.items; track d.id) {
              <li [class.on]="d.id === current().id" (click)="select(d.id, true)">
                <span class="num" [class]="d.kind" [class.done]="d.state === 'done'" [class.review]="d.state === 'review'">{{ d.n }}</span>
                <span class="what">{{ d.text | t }}</span>
                <span class="got">{{ d.shown }}</span>
              </li>
            }
          </ol>
        }
        <h2 class="h">{{ 'Your own' | t }}</h2>
        <ol>
          @for (x of extras(); track x.id) {
            <li [class.on]="x.id === current().id" (click)="select(x.id, true)">
              <span class="num extra">+</span>
              <span class="what">{{ x.label || ('Not named yet' | t) }}</span>
              <span class="got">{{ x.shown }}</span>
            </li>
          }
        </ol>
        <div class="steps">
          <button type="button" class="btn btn-sm" (click)="addExtra()">{{ '+ Something the plan missed' | t }}</button>
          <button type="button" class="btn btn-sm" (click)="showText.set(!showText())">{{ showText() ? ('Hide' | t) : ('As text' | t) }}</button>
        </div>
        @if (showText()) {
          <textarea class="as-text" readonly rows="10" [value]="text()" (focus)="$any($event.target).select()"></textarea>
        }
        </section>
      </div>
    </div>
  `,
  styles: `
    .survey { display: grid; gap: 0.9rem; }
    .survey app-page-intro { margin-bottom: -1rem; }
    .lede { margin: 0.1rem 0 0; font-size: 0.8125rem; }
    .status, .steps, .value { display: flex; gap: 0.5rem; flex-wrap: wrap; }
    /* The sketch, what is being typed and the list, all three in view: stacked on a phone; on an
       upright tablet the sketch across the top and the other two side by side; on a screen lying
       down, three columns as tall as the screen, each scrolling on its own. */
    .work { display: grid; gap: 0.75rem; grid-template-areas: 'sketch' 'entry' 'list'; }
    .sketch-card { grid-area: sketch; padding: 0.4rem; min-width: 0; }
    .entry { grid-area: entry; min-width: 0; container-type: inline-size; display: flex; flex-direction: column; --f: 1rem; }
    .entry > * { font-size: var(--f); flex-shrink: 0; }
    .entry .small { font-size: 0.82em; }
    .entry > .small { font-size: calc(var(--f) * 0.82); }
    .list { grid-area: list; min-width: 0; max-height: 42vh; overflow: auto; }
    .sketch { height: 46vh; min-height: 260px; }
    @media (min-width: 700px) {
      .work { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); grid-template-areas: 'sketch sketch' 'entry list'; align-items: start; }
      .list { max-height: 40vh; }
    }
    @media (min-width: 1000px) and (orientation: landscape) {
      /* The page is the screen's height below the portal's header (59 px, and 24 above and below the
         page), and the three columns take what the title leaves: nothing scrolls but each column. */
      .survey { display: flex; flex-direction: column; height: calc(100dvh - 107px); }
      .work {
        flex: 1;
        grid-template-columns: minmax(12rem, 17rem) minmax(20rem, 1fr) minmax(15rem, 21rem);
        grid-template-areas: 'list sketch entry';
        /* One row the height left, not as tall as the list: or the list would never scroll. */
        grid-template-rows: minmax(0, 1fr);
        align-items: stretch;
        min-height: 26rem;
      }
      .sketch-card { display: flex; flex-direction: column; min-height: 0; }
      .sketch { flex: 1; height: auto; min-height: 0; }
      .entry, .list { max-height: none; min-height: 0; overflow: auto; }
      .entry { --f: clamp(1rem, 5.6cqi, 1.35rem); overflow: hidden; }
    }
    .sketch svg { width: 100%; height: 100%; display: block; touch-action: pan-y; }
    .gesture-hint { margin-left: auto; font-size: 0.75rem; }
    /* The whole block lets a finger scroll the page past it; zoomed in, a finger moves the sketch. */
    .sketch svg.zoomed { touch-action: none; cursor: grab; }
    .key { display: flex; flex-wrap: wrap; align-items: center; gap: 0.2rem 1rem; padding: 0.4rem 0.3rem 0.1rem; color: var(--text-muted); }
    .swatch { display: inline-block; width: 1.1rem; height: 3px; margin-right: 0.4rem; vertical-align: middle; background: var(--c); }
    svg * { vector-effect: non-scaling-stroke; }
    .road { fill: color-mix(in srgb, var(--text) 12%, transparent); }
    .walk { fill: #d9ccae8c; stroke: #b9ab8c; stroke-width: 0.6; }
    .built, .other { stroke: var(--text-muted); stroke-width: 0.8; }
    .built { fill: color-mix(in srgb, var(--primary) 22%, transparent); }
    .other { fill: color-mix(in srgb, var(--text) 14%, transparent); }
    .open { fill: none; stroke: var(--text-muted); stroke-width: 0.8; stroke-dasharray: 3 2; }
    .street, text { text-anchor: middle; dominant-baseline: central; pointer-events: none; }
    .street { fill: var(--text-muted); font-weight: 600; }
    g { cursor: pointer; }
    g line { stroke-width: 2; stroke: currentColor; }
    g line.ext { stroke-width: 1; stroke-dasharray: 3; }
    g .end, g .dot { fill: currentColor; }
    g text { fill: #fff; font-weight: 700; }
    .setback, .setback-key { color: #e8590c; --c: #e8590c; }
    .length, .length-key { color: #1c7ed6; --c: #1c7ed6; }
    .done { color: #2f9e44; --c: #2f9e44; }
    /* Taken, but to take again: its own colour, apart from pending and taken. */
    .review, .review-key { color: #ae3ec9; --c: #ae3ec9; }
    .review-badge { background: #ae3ec92e; color: #ae3ec9; }
    .review-note { margin: 0.2rem 0 0; color: #ae3ec9; }
    g.on line { stroke-width: 4; }
    g.on .dot { stroke: var(--bg-elevated); stroke-width: 2.5; }
    .title { display: flex; gap: 0.6rem; align-items: flex-start; }
    .num { flex: none; display: inline-grid; place-items: center; min-width: 1.7rem; height: 1.7rem; border-radius: 999px; background: var(--c, #868e96); color: #fff; font-size: 0.75rem; font-weight: 700; }
    .entry input { width: 100%; min-height: 2.6em; }
    .value { margin-top: 0.7rem; flex-wrap: nowrap; }
    .value input { font-size: 1.8em; }
    .value .btn { min-width: 2.6em; font-size: 1.2em; }
    .title .num { min-width: 2em; height: 2em; font-size: 0.8em; }
    .steps .btn { flex: 1; min-height: 2.8em; font-size: 0.95em; white-space: nowrap; }
    .next { margin-top: auto; padding-top: 0.8rem; border: 0; background: none; color: inherit; text-align: left; font: inherit; font-size: 0.85em; }
    .label-in { margin-top: 0.6rem; }
    .reading { min-height: 1.2em; margin: 0.35rem 0; color: var(--text-muted); }
    .reading.bad { color: var(--danger); }
    .steps { margin: 0.6rem 0; }
    .examples { margin-top: 0.4rem; color: var(--text-muted); }
    .list ol { list-style: none; margin: 0 0 0.6rem; padding: 0; }
    .list li { display: flex; gap: 0.6rem; align-items: center; padding: 0.3rem 0.2rem; border-radius: 6px; cursor: pointer; font-size: 0.875rem; }
    .list li.on { background: color-mix(in srgb, var(--primary) 14%, transparent); }
    .what { flex: 1; }
    .got { font-variant-numeric: tabular-nums; }
    .as-text { width: 100%; font-family: var(--font-mono); font-size: 0.75rem; }
  `,
})
export class SurveyPage {
  private readonly buildingsService = inject(BuildingsService);
  private readonly grounds = inject(GroundService);
  private readonly structuresService = inject(StructuresService);
  private readonly surveyService = inject(SurveyService);
  private readonly sketchBox = viewChild<ElementRef<HTMLElement>>('sketchBox');
  private readonly listBox = viewChild<ElementRef<HTMLElement>>('listBox');
  private readonly entryBox = viewChild<ElementRef<HTMLElement>>('entryBox');

  /** Whatever the distance says, the entry fits its column: refitted when it or what is typed changes. */
  private readonly refit = afterRenderEffect(() => {
    this.current();
    this.reading();
    this.fitEntry();
  });
  private readonly injector = inject(Injector);

  readonly plan = SURVEY_PLAN;
  readonly block = SURVEY_BLOCK;
  readonly pieceMetres = PIECE_METRES;
  /** What goes in a note, to tap in and finish: what the sketch cannot show. */
  readonly noteExamples = [
    /* i18n */ 'Columns in front: measured to the wall behind',
    /* i18n */ 'In pieces round a tree / post / car',
    /* i18n */ 'The wall steps in here, … m',
    /* i18n */ 'The floors above come out … m',
    /* i18n */ 'The door is … m further on',
    /* i18n */ 'No door here',
    /* i18n */ 'To a fence or railing, not a wall',
    /* i18n */ 'The curb is a driveway ramp here',
    /* i18n */ 'Could not reach it: estimated',
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
  /** Where a pinch or a drag has taken the sketch; null for the window round the current distance. */
  private readonly userBox = signal<Box | null>(null);
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

  readonly done = computed(() => this.plan.filter((d) => this.stateOf(d.id) === 'done').length);
  readonly toReview = computed(() => this.plan.filter((d) => this.stateOf(d.id) === 'review').length);

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
    return { id, n: null, street: t('Something the plan missed'), kind: 'extra' as const, text: label || t('Not named yet'), hint: undefined, expected: 0 };
  });

  /** Where Next goes: the next distance not taken yet, shown at the foot of the entry. */
  readonly upNext = computed(() => {
    const order = this.plan.map((d) => d.id);
    const at = order.indexOf(this.currentId());
    for (let i = 1; i < order.length; i++) {
      const id = order[(Math.max(at, -1) + i) % order.length];
      if (this.stateOf(id) !== 'done') return this.plan.find((d) => d.id === id) ?? null;
    }
    return null;
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
    if (r.metres === null) return c.kind === 'setback' ? t('Metres from the wall to the curb.') : t('Metres.');
    const text = this.values().get(c.id)?.text;
    const total = r.pieces > 1 ? t('= {total} in {pieces} pieces', { total: formatMetres(r.metres, text), pieces: r.pieces }) : formatMetres(r.metres, text);
    if (!c.expected) return total;
    const delta = r.metres - c.expected;
    return r.off
      ? t('{total} · the model says {expected}: check it, then note what differs', { total, expected: formatMetres(c.expected, text) })
      : t('{total} · {delta} from the model', { total, delta: `${delta >= 0 ? '+' : '−'}${formatMetres(Math.abs(delta), text)}` });
  });

  readonly groups = computed(() => {
    const out: { street: string; items: (PlannedDistance & { state: DistanceState; shown: string })[] }[] = [];
    for (const d of this.plan) {
      let g = out.find((x) => x.street === d.street);
      if (!g) out.push((g = { street: d.street, items: [] }));
      const m = this.values().get(d.id);
      g.items.push({ ...d, state: this.stateOf(d.id), shown: m ? this.shown(m) : '' });
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
        // What the upper floors carry out over the street is no wall to measure to: dashed, like open ground.
        .flatMap((b) => (b.footprint ?? []).map((p) => shape(p.ring, (p.lowestFloor ?? 1) > 1 ? 0 : p.floors, true))),
      ...this.structures().filter((s) => near(s.ring)).map((s) => shape(s.ring, s.floors, false)),
    ];
    // Where each line and number goes was laid out by scripts/survey-plan.py, clear of the others.
    const lines = planned.map(({ d, a, b }) => {
      const state = this.stateOf(d.id);
      const dim = d.dim ? ([at(d.dim[0]), at(d.dim[1])] as const) : null;
      return { id: d.id, n: d.n, a, b, dim, tag: at(d.tag), cls: state === 'todo' ? d.kind : state };
    });
    const streets: { name: string; x: number; y: number; angle: number }[] = [];
    for (const street of ground.streets) {
      const label = streetLabel(street.path.map(at), box, 40);
      if (label && !streets.some((s) => s.name === street.name)) streets.push({ name: street.name, ...label });
    }
    return { box, roadways: area(ground.roadways), sidewalks: area(ground.sidewalks), shapes, lines, streets };
  });

  /**
   * The lines as drawn, with the numbers that fit: only those that do not touch a number already
   * shown, the current one first - close up that is all of them.
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
      if (kept.every((t) => Math.hypot(t.x - l.tag.x, t.y - l.tag.y) >= room)) {
        kept.push(l.tag);
        shown.add(l.id);
      }
    }
    return { ...v, lines: v.lines.map((l) => ({ ...l, shown: shown.has(l.id) })) };
  });

  /** Where the sketch is looking: where a pinch or a drag left it, else a window round the current distance. */
  readonly box = computed<Box>(() => {
    const v = this.view();
    if (!v) return { x: 0, y: 0, width: 1, height: 1 };
    const user = this.userBox();
    if (user) return user;
    const line = v.lines.find((l) => l.id === this.currentId());
    if (!line) return v.box;
    const pts = [line.a, line.b, line.tag, ...(line.dim ?? [])];
    const xs = pts.map((p) => p.x);
    const ys = pts.map((p) => p.y);
    const size = Math.max(ZOOM_MIN, Math.max(...xs) - Math.min(...xs) + 10, Math.max(...ys) - Math.min(...ys) + 10);
    const cx = (Math.max(...xs) + Math.min(...xs)) / 2;
    const cy = (Math.max(...ys) + Math.min(...ys)) / 2;
    return { x: cx - size / 2, y: cy - size / 2, width: size, height: size };
  });

  readonly viewBox = computed(() => {
    const b = this.box();
    return `${b.x} ${b.y} ${b.width} ${b.height}`;
  });

  /** Closer than the whole block: a finger then moves the sketch rather than the page. */
  readonly zoomed = computed(() => {
    const whole = this.view()?.box;
    const b = this.box();
    return !!whole && (b.width < whole.width * 0.98 || b.height < whole.height * 0.98);
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
        saved: t('Saved'),
        waiting: t('Saving soon'),
        saving: t('Saving…'),
        offline: t('Kept on this device, not saved yet'),
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
      const entry = this.entryBox()?.nativeElement;
      if (!entry) return;
      const fit = new ResizeObserver(() => this.fitEntry());
      fit.observe(entry);
      destroyRef.onDestroy(() => fit.disconnect());
    });
  }

  /**
   * Lying down, the entry is as tall as the screen and never scrolls: when what it holds - a long
   * description, a hint, the reading - is taller, its text comes down until it all fits. Upright it
   * takes the height it needs and nothing changes.
   */
  private fitEntry(): void {
    const el = this.entryBox()?.nativeElement;
    if (!el) return;
    el.style.removeProperty('--f');
    if (getComputedStyle(el).overflowY !== 'hidden') return;
    const first = el.firstElementChild as HTMLElement | null;
    for (let i = 0; i < 6 && first && el.scrollHeight > el.clientHeight + 1; i++) {
      const size = parseFloat(getComputedStyle(first).fontSize);
      el.style.setProperty('--f', `${Math.max(11, size * (el.clientHeight / el.scrollHeight) * 0.97)}px`);
    }
  }

  /** The model's figure, written as the portal's language writes decimals. */
  metres(value: number): string {
    return formatMetres(value);
  }

  // ------------------------------------------------------------------ zooming and moving

  /** A pinch, or Ctrl/⌘ and the wheel: closer or further about that point, out as far as the whole block. */
  zoomAt(step: ZoomStep): void {
    const whole = this.view()?.box;
    const at = this.toSketch(step.x, step.y);
    if (!whole || !at) return;
    const b = this.box();
    // Never closer than CLOSEST metres across; once further out than the block, the whole block.
    const f = Math.min(step.factor, Math.min(b.width, b.height) / CLOSEST);
    if (!(f > 0) || f === 1) return;
    const width = b.width / f;
    const height = b.height / f;
    if (width >= whole.width || height >= whole.height) {
      this.userBox.set(whole);
      return;
    }
    this.userBox.set(this.clamp({ x: at.x - (at.x - b.x) / f, y: at.y - (at.y - b.y) / f, width, height }));
  }

  /** Two fingers on a trackpad, or the wheel, move a sketch that is zoomed in; the whole block lets the page scroll. */
  onWheel(event: WheelEvent): void {
    if (event.ctrlKey || event.metaKey || !this.zoomed()) return;
    event.preventDefault();
    this.panBy(event.deltaX, event.deltaY);
  }

  private drag: { id: number; x: number; y: number; moved: boolean } | null = null;
  private dragged = false;

  onDown(event: PointerEvent): void {
    if (!this.zoomed() || (event.pointerType === 'mouse' && event.button !== 0) || this.drag) return;
    this.drag = { id: event.pointerId, x: event.clientX, y: event.clientY, moved: false };
    this.dragged = false;
  }

  onMove(event: PointerEvent): void {
    const drag = this.drag;
    if (!drag || drag.id !== event.pointerId) return;
    const dx = event.clientX - drag.x;
    const dy = event.clientY - drag.y;
    if (!drag.moved && Math.hypot(dx, dy) < 5) return;
    if (!drag.moved) {
      // Keeps the drag when the finger leaves the sketch; a pointer that cannot be captured still drags.
      try {
        (event.currentTarget as Element).setPointerCapture?.(event.pointerId);
      } catch {
        /* nothing to capture */
      }
    }
    drag.moved = true;
    this.panBy(-dx, -dy);
    drag.x = event.clientX;
    drag.y = event.clientY;
  }

  onUp(event: PointerEvent): void {
    if (this.drag?.id !== event.pointerId) return;
    this.dragged = this.drag.moved;
    this.drag = null;
  }

  /** A drag that moved the sketch is not also a tap on the distance it started over. */
  onClickCapture(event: Event): void {
    if (!this.dragged) return;
    this.dragged = false;
    event.stopPropagation();
    event.preventDefault();
  }

  /** Moves the sketch by screen pixels. */
  private panBy(dx: number, dy: number): void {
    const b = this.box();
    const { w, h } = this.size();
    const perPixel = Math.max(b.width / w, b.height / h);
    this.userBox.set(this.clamp({ ...b, x: b.x + dx * perPixel, y: b.y + dy * perPixel }));
  }

  /** Keeps a view on the block: it may not wander off past the whole block's edges. */
  private clamp(b: Box): Box {
    const whole = this.view()?.box;
    if (!whole) return b;
    const x = Math.min(Math.max(b.x, whole.x - b.width / 2), whole.x + whole.width - b.width / 2);
    const y = Math.min(Math.max(b.y, whole.y - b.height / 2), whole.y + whole.height - b.height / 2);
    return { ...b, x, y };
  }

  /** A point of the screen on the sketch, as the SVG draws it with its view box met in the middle. */
  private toSketch(clientX: number, clientY: number): ViewPoint | null {
    const el = this.sketchBox()?.nativeElement.querySelector('svg');
    if (!el) return null;
    const rect = el.getBoundingClientRect();
    const b = this.box();
    const s = Math.min(rect.width / b.width, rect.height / b.height);
    if (!s) return null;
    const ox = (rect.width - b.width * s) / 2;
    const oy = (rect.height - b.height * s) / 2;
    return { x: b.x + (clientX - rect.left - ox) / s, y: b.y + (clientY - rect.top - oy) / s };
  }

  valueOf(id: string): SurveyMeasure {
    return this.values().get(id) ?? { id };
  }

  select(id: string, scrollUp = false): void {
    this.currentId.set(id);
    this.userBox.set(null);
    // Stacked, the sketch is above the list; side by side it is already in view and nothing moves.
    if (scrollUp) this.sketchBox()?.nativeElement.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' });
    // The list keeps the distance chosen in view, however it was chosen: Next, Back, the sketch.
    // At once, not smoothly: a smooth scroll is dropped by a browser that is not drawing frames.
    afterNextRender(() => this.listBox()?.nativeElement.querySelector('li.on')?.scrollIntoView?.({ block: 'nearest' }), {
      injector: this.injector,
    });
  }

  /** The next or previous distance of the walk; Next skips the ones already taken. */
  step(by: 1 | -1): void {
    const order = [...this.plan.map((d) => d.id), ...this.extras().map((x) => x.id)];
    const at = order.indexOf(this.currentId());
    for (let i = 1; i <= order.length; i++) {
      const id = order[(at + by * i + order.length * i) % order.length];
      if (by < 0 || this.stateOf(id) !== 'done') {
        this.select(id);
        return;
      }
    }
    this.select(order[(at + by + order.length) % order.length]);
  }

  /** An example added to the note, to finish by hand: the cursor goes to its "…". */
  addNote(id: string, example: string, box: HTMLInputElement): void {
    const note = (this.valueOf(id).note ?? '').trim();
    const said = t(example);
    const next = note ? `${note}; ${said}` : said;
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

  /** Pending, taken, or taken but asked to be taken again. */
  stateOf(id: string): DistanceState {
    const m = this.values().get(id);
    if (m?.recheck) return 'review';
    return readDistance(m?.text).metres !== null ? 'done' : 'todo';
  }

  shownOf(id: string): string {
    const m = this.values().get(id);
    return m ? this.shown(m) : '';
  }

  /** What a distance asked to be taken again read before, while it is being typed anew. */
  readonly before = signal<Record<string, string>>({});

  /** Asks for a distance to be taken again, keeping what was taken; or takes the ask back. */
  toggleRecheck(id: string): void {
    this.change(id, { recheck: !this.valueOf(id).recheck });
  }

  private change(id: string, patch: Partial<SurveyMeasure>): void {
    const next: SurveyMeasure = { ...this.valueOf(id), ...patch, id };
    delete next.updatedAt;
    // Typing it again is taking it again, even with the same figure.
    if ('text' in patch) {
      const was = this.values().get(id);
      if (was?.recheck) this.before.update((b) => ({ ...b, [id]: this.shown(was) }));
      next.metres = readDistance(next.text).metres;
      delete next.recheck;
    }
    // Only an ask is sent: a distance nobody asked for again carries nothing.
    if (!next.recheck) delete next.recheck;
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

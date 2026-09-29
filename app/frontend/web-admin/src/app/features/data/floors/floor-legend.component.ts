import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  CATEGORY_COLORS,
  CATEGORY_LABELS,
  SPACE_CATEGORIES,
  type SpaceCategory,
} from '../spaces/space.model';
import { isPlaced, isUnidentified, type DraftSpace } from './floor-draft';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/**
 * The key under the plan: what each fill means and what each mark is.
 *
 * <p>It was a line of small grey words beside dots, read as an afterthought and skipped. The
 * fills are chips now, in the plan's own colours and with how many rooms of each the floor
 * holds, so the key also says how far the floor is from named - "Not identified yet 41" is the
 * number the walk on site brings down. The marks are drawn with the same strokes the plan draws
 * them with, so they match what is on screen rather than describe it.
 */
@Component({
  selector: 'app-floor-legend',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="legend" [attr.aria-label]="'What the plan shows' | t">
      <div class="group">
        <h3>{{ 'Spaces' | t }}</h3>
        <ul class="chips">
          @for (item of fills(); track item.category) {
            <li class="chip" [class.empty]="item.count === 0" [style.--fill]="item.color">
              <span class="swatch" aria-hidden="true"></span>
              <span class="name">{{ item.label | t }}</span>
              <span class="count" [attr.aria-label]="item.count === 1 ? ('{n} room' | t: { n: 1 }) : ('{n} rooms' | t: { n: item.count })">
                {{ item.count }}
              </span>
            </li>
          }
        </ul>
      </div>

      <div class="group">
        <h3>{{ 'On the plan' | t }}</h3>
        <ul class="marks">
          <li>
            <svg viewBox="0 0 28 18" aria-hidden="true">
              <rect class="outline" x="2" y="2" width="24" height="14" />
            </svg>
            {{ 'Building outline' | t }}
          </li>
          <li>
            <svg viewBox="0 0 28 18" aria-hidden="true">
              <line class="wall" x1="1" y1="9" x2="27" y2="9" />
              <line class="door-casing" x1="8" y1="9" x2="20" y2="9" />
              <line class="door" x1="8" y1="9" x2="20" y2="9" />
            </svg>
            {{ 'Door' | t }}
          </li>
          <li>
            <svg viewBox="0 0 28 18" aria-hidden="true">
              <rect class="stairs" x="2" y="2" width="24" height="14" [attr.fill]="stairsFill" />
              @for (x of treads; track x) {
                <line class="tread" [attr.x1]="x" y1="2" [attr.x2]="x" y2="16" />
              }
            </svg>
            {{ 'Stairs' | t }}
          </li>
          <li>
            <svg viewBox="0 0 28 18" aria-hidden="true">
              <polyline class="corridor" points="2,14 10,6 18,12 26,4" />
            </svg>
            {{ 'Corridor' | t }}
          </li>
          <li>
            <svg viewBox="0 0 28 18" aria-hidden="true">
              <rect class="room unidentified" x="2" y="2" width="24" height="14" [attr.fill]="unidentifiedFill" />
            </svg>
            {{ 'Drawn, not named yet' | t }}
          </li>
          <li>
            <svg viewBox="0 0 28 18" aria-hidden="true">
              <rect class="room problem" x="2" y="2" width="24" height="14" [attr.fill]="unidentifiedFill" />
            </svg>
            {{ 'Needs a look' | t }}
          </li>
          @for (wing of marginWings(); track wing.code) {
            <li>
              <svg viewBox="0 0 28 18" aria-hidden="true">
                <rect [attr.class]="'margin wing-' + wing.code" x="2" y="2" width="24" height="14" />
              </svg>
              {{ '{name}: where its rooms go on this floor' | t: { name: wing.name } }}
            </li>
          }
          @if (marginWings().length) {
            <li>
              <svg viewBox="0 0 28 18" aria-hidden="true">
                <rect class="margin below" x="2" y="2" width="24" height="14" />
              </svg>
              {{ 'Where the floor below’s went' | t }}
            </li>
          }
          @if (neighbours()) {
            <li>
              <svg viewBox="0 0 28 18" aria-hidden="true">
                <rect class="neighbour" x="2" y="2" width="24" height="14" />
              </svg>
              {{ 'A building next door' | t }}
            </li>
          }
          @if (groundSource()) {
            <li>
              <svg viewBox="0 0 28 18" aria-hidden="true">
                <rect class="block" x="2" y="2" width="24" height="14" />
              </svg>
              {{ 'City block' | t }}
            </li>
            <li>
              <svg viewBox="0 0 28 18" aria-hidden="true">
                <rect class="sidewalk" x="1" y="5" width="26" height="8" />
              </svg>
              {{ 'Sidewalk' | t }}
            </li>
            <li>
              <svg viewBox="0 0 28 18" aria-hidden="true">
                <rect class="roadway" x="1" y="4" width="26" height="10" />
              </svg>
              {{ 'Street' | t }}
            </li>
            @if (cadastre()) {
              <li>
                <svg viewBox="0 0 28 18" aria-hidden="true">
                  <rect class="cadastre reaches" x="2" y="2" width="24" height="14" />
                </svg>
                {{ 'The building on this floor, per the cadastre' | t }}
              </li>
              <li>
                <svg viewBox="0 0 28 18" aria-hidden="true">
                  <rect class="cadastre" x="2" y="2" width="24" height="14" />
                </svg>
                {{ 'On the floor below, per the cadastre' | t }}
              </li>
            }
          }
        </ul>
        @if (groundSource(); as source) {
          <p class="credit">{{ 'Streets and blocks: {source}' | t: { source: source } }}</p>
        }
      </div>
    </section>
  `,
  styles: `
    .legend {
      display: grid;
      gap: 0.75rem;
      margin-top: 0.75rem;
      padding: 0.75rem 0.875rem;
      border: 1px solid var(--border);
      border-radius: var(--radius-md);
      background: var(--bg-inset);
    }
    h3 {
      margin: 0 0 0.4rem;
      font-size: 0.6875rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.07em;
      color: var(--text-faint);
    }
    ul {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-wrap: wrap;
    }

    /* A chip in the plan's own fill. The fills are pale in both themes, so the ink is fixed,
       as it is on the plan's labels, rather than the theme's text. */
    .chips {
      gap: 0.375rem;
    }
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.25rem 0.3rem 0.25rem 0.5rem;
      border-radius: 999px;
      background: var(--fill);
      border: 1px solid color-mix(in srgb, var(--fill) 70%, #1c2128);
      color: #1c2128;
      font-size: 0.75rem;
      font-weight: 600;
      line-height: 1.2;
    }
    .swatch {
      width: 0.5rem;
      height: 0.5rem;
      border-radius: 50%;
      background: color-mix(in srgb, var(--fill) 45%, #1c2128);
    }
    .count {
      min-width: 1.5rem;
      padding: 0.05rem 0.4rem;
      border-radius: 999px;
      background: rgb(255 255 255 / 75%);
      text-align: center;
      font-variant-numeric: tabular-nums;
    }
    /* Still in the key - it says what the colour would mean - but out of the way. */
    .chip.empty {
      opacity: 0.55;
    }

    .marks {
      gap: 0.4rem 1rem;
    }
    .marks li {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      font-size: 0.75rem;
      color: var(--text-muted);
    }
    .marks svg {
      width: 28px;
      height: 18px;
      flex: 0 0 auto;
      overflow: visible;
    }
    /* The strokes below are the plan's own (floor-plan.component.ts). */
    .outline {
      fill: color-mix(in srgb, var(--text) 4%, transparent);
      stroke: color-mix(in srgb, var(--text) 70%, transparent);
      stroke-width: 2;
    }
    .wall {
      stroke: rgb(28 33 40 / 55%);
      stroke-width: 1;
    }
    .door-casing {
      stroke: #fff;
      stroke-width: 7;
    }
    .door {
      stroke: #0f766e;
      stroke-width: 4;
    }
    .stairs {
      stroke: rgb(28 33 40 / 55%);
      stroke-width: 1;
    }
    .tread {
      stroke: #1c2128;
      stroke-opacity: 0.45;
      stroke-width: 1;
    }
    .corridor {
      fill: none;
      stroke: #5b8def;
      stroke-width: 3;
      stroke-linecap: round;
      stroke-linejoin: round;
      opacity: 0.85;
    }
    .room {
      stroke: rgb(28 33 40 / 55%);
      stroke-width: 1;
    }
    .room.unidentified {
      stroke-dasharray: 5 3;
      stroke-width: 1.5;
    }
    .room.problem {
      stroke: var(--danger);
      stroke-width: 2;
    }
    /* The ground's layers, as floor-plan.component.ts draws them. */
    .block {
      fill: var(--bg-elevated);
      stroke: color-mix(in srgb, var(--text) 45%, transparent);
      stroke-width: 1.5;
    }
    .sidewalk {
      fill: color-mix(in srgb, #d8c9ad 70%, var(--bg-elevated));
      stroke: color-mix(in srgb, var(--text) 22%, transparent);
      stroke-width: 0.75;
    }
    .roadway {
      fill: color-mix(in srgb, var(--text) 16%, var(--bg-elevated));
    }
    .cadastre {
      fill: none;
      stroke: color-mix(in srgb, var(--nav-active-edge) 60%, transparent);
      stroke-width: 2;
      stroke-dasharray: 0 4;
      stroke-linecap: round;
    }
    .margin {
      --wing: var(--margin-other);
      fill: color-mix(in srgb, var(--wing) 11%, transparent);
      stroke: var(--wing);
      stroke-width: 1.5;
    }
    .margin.wing-N {
      --wing: var(--margin-n);
    }
    .margin.wing-C,
    .margin.wing-none {
      --wing: var(--margin-c);
    }
    .margin.wing-S {
      --wing: var(--margin-s);
    }
    .neighbour {
      fill: color-mix(in srgb, var(--text) 7%, transparent);
      stroke: color-mix(in srgb, var(--text) 45%, transparent);
      stroke-width: 1.5;
      stroke-dasharray: 4 3;
    }
    .margin.below {
      --wing: var(--margin-c);
      fill: none;
      stroke-width: 2;
      stroke-dasharray: 0 4;
      stroke-linecap: round;
    }
    .cadastre.reaches {
      fill: color-mix(in srgb, var(--nav-active-edge) 7%, transparent);
      stroke: var(--nav-active-edge);
      stroke-dasharray: none;
    }
    .credit {
      margin: 0.5rem 0 0;
      font-size: 0.6875rem;
      color: var(--text-faint);
    }

    @media (min-width: 900px) {
      .legend {
        grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
        column-gap: 1.25rem;
      }
    }
  `,
})
export class FloorLegendComponent {
  readonly spaces = input.required<readonly DraftSpace[]>();
  /** Type code to category, as the plan colours them. */
  readonly categories = input.required<ReadonlyMap<string, SpaceCategory>>();
  /** Where the streets under the plan come from, while they are drawn; null when they are not. */
  readonly groundSource = input<string | null>(null);
  /** Whether the building's cadastral outline is drawn with them. It has its own switch. */
  readonly cadastre = input(false);
  /** The wings whose margin is drawn, each by the name its building gives it. */
  readonly marginWings = input<readonly { code: string; name: string }[]>([]);
  /** Whether the buildings next door are drawn. */
  readonly neighbours = input(false);

  readonly stairsFill = CATEGORY_COLORS.CIRCULATION;
  readonly unidentifiedFill = CATEGORY_COLORS.OTHER;
  readonly treads = [6, 10, 14, 18, 22];

  /** Every category, with how many of the floor's placed rooms the plan fills with it. */
  readonly fills = computed(() => {
    const counts = new Map<SpaceCategory, number>();
    for (const space of this.spaces()) {
      if (!isPlaced(space)) continue;
      const category = isUnidentified(space) ? 'OTHER' : (this.categories().get(space.typeCode) ?? 'OTHER');
      counts.set(category, (counts.get(category) ?? 0) + 1);
    }
    return SPACE_CATEGORIES.map((category) => ({
      category,
      label: CATEGORY_LABELS[category],
      color: CATEGORY_COLORS[category],
      count: counts.get(category) ?? 0,
    }));
  });
}

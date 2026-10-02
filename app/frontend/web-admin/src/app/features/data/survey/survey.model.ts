import type { Coordinate } from '../ground/ground.model';

/**
 * One distance of the survey plan (scripts/survey-plan.py): from a wall straight out to the curb
 * (`setback`), or along a wall (`length`), with where it runs on the earth and what the model
 * says it measures.
 */
export interface PlannedDistance {
  /** Its place in the walk round the block. */
  n: number;
  id: string;
  street: string;
  kind: 'setback' | 'length';
  building: string;
  text: string;
  /** What Street View shows there, when it changes where the tape goes. */
  hint?: string;
  /** On the wall. */
  a: Coordinate;
  /** On the curb, for a setback; the wall's other end, for a length. */
  b: Coordinate;
  /** For a length: its dimension line, drawn beside the wall, clear of the other lines. */
  dim?: Coordinate[];
  /** Where its number goes on the sketch. */
  tag: Coordinate;
  /** Metres, by the model. */
  expected: number;
}

/** Mirrors SurveyMeasure in docs/api/map.openapi.yaml: one distance as taken on site. */
export interface SurveyMeasure {
  id: string;
  /** What an extra distance is of; absent for one the plan names. */
  label?: string | null;
  /** As typed: "4,80 + 3,25" when taken in pieces. */
  text?: string | null;
  metres?: number | null;
  note?: string | null;
  /** True while it is asked to be taken again: what was typed stays, for reference, until it is. */
  recheck?: boolean | null;
  updatedAt?: string | null;
}

/** Mirrors Survey: every distance taken round a campus's blocks, at the version it was read. */
export interface Survey {
  campus: string;
  measures: SurveyMeasure[];
  version: number;
  updatedAt?: string | null;
}

/** Mirrors SurveyRequest. */
export interface SurveyRequest {
  version: number;
  measures: SurveyMeasure[];
}

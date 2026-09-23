import type { Accessibility } from '../buildings/building.model';

/**
 * Mirrors SpaceCategory: the fixed family a type belongs to. Clients draw by category, which is
 * why this list is closed while the types under it are data.
 */
export const SPACE_CATEGORIES = [
  'TEACHING',
  'PUBLIC_SERVICE',
  'OFFICE',
  'SOCIAL',
  'FACILITIES',
  'CIRCULATION',
  'OTHER',
] as const;
export type SpaceCategory = (typeof SPACE_CATEGORIES)[number];

export const CATEGORY_LABELS: Record<SpaceCategory, string> = {
  TEACHING: 'Teaching',
  PUBLIC_SERVICE: 'Public service',
  OFFICE: 'Office',
  SOCIAL: 'Social and wellbeing',
  FACILITIES: 'Facilities',
  CIRCULATION: 'Circulation',
  OTHER: 'Not identified yet',
};

/** Mirrors SpaceType: an entry of the catalogue the Space types screen edits. */
export interface SpaceType {
  code: string;
  name: string;
  category: SpaceCategory;
}

/**
 * Mirrors Space in docs/api/map.openapi.yaml.
 *
 * Two codes, kept apart on purpose: `code` identifies the space and is never shown; `doorCode`
 * is what is printed on the door, and is absent when nothing is. A generated code on screen
 * looks exactly like a real one.
 */
export interface Space {
  id: string;
  code: string;
  doorCode?: string | null;
  baseCode?: string | null;
  wing?: string | null;
  name: string;
  typeCode: string;
  typeName?: string;
  category?: SpaceCategory;
  buildingId: string;
  buildingCode: string;
  campus: string;
  floorCode: string;
  floorLevel: number;
  aliases: string[];
  /** Absent while the space is inventoried but not placed on the grid. */
  gridRow?: number | null;
  gridColumn?: number | null;
  rowSpan: number;
  colSpan: number;
  accessVia?: string | null;
  /** The space's own value; absent when it takes the floor's. */
  accessibility?: Accessibility | null;
  effectiveAccessibility: Accessibility;
  note?: string | null;
  capacity?: number;
}

/** Mirrors SpaceRequest - the create/update payload. */
export interface SpaceRequest {
  code: string;
  doorCode?: string | null;
  wing?: string | null;
  name: string;
  typeCode: string;
  buildingCode: string;
  floorCode: string;
  aliases: string[];
  gridRow?: number | null;
  gridColumn?: number | null;
  rowSpan?: number;
  colSpan?: number;
  accessVia?: string | null;
  accessibility?: Accessibility | null;
  note?: string | null;
  capacity?: number;
}

export interface SpaceSearchFilters {
  /** Omit to list rather than search: the server lists by the other filters. */
  q?: string;
  page: number;
  size: number;
  campus?: string;
  type?: string;
  category?: SpaceCategory;
  buildingCode?: string;
  wing?: string;
  floor?: string;
}

/** What to put on screen for a space: its door code, or nothing - never its internal code. */
export function shownCode(space: Pick<Space, 'doorCode'>): string | null {
  return space.doorCode ?? null;
}

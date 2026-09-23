/** Mirrors GridPoint in docs/api/map.openapi.yaml. Zero-based, row 0 at the top as drawn. */
export interface GridPoint {
  row: number;
  col: number;
}

/** Mirrors Corridor. */
export interface Corridor {
  code: string;
  name: string;
  color: string;
  path: GridPoint[];
}

/** Mirrors Accessibility: whether a place is reachable without stairs. UNKNOWN is never read as either. */
export const ACCESSIBILITY = ['UNKNOWN', 'STEP_FREE', 'STAIRS_ONLY'] as const;
export type Accessibility = (typeof ACCESSIBILITY)[number];

export const ACCESSIBILITY_LABELS: Record<Accessibility, string> = {
  UNKNOWN: 'Not checked yet',
  STEP_FREE: 'Step-free (lift or ramp)',
  STAIRS_ONLY: 'Stairs only',
};

/** Mirrors FloorStatus: how far a floor is from being trusted. */
export const FLOOR_STATUSES = ['UNMAPPED', 'DRAFT', 'VERIFIED'] as const;
export type FloorStatus = (typeof FLOOR_STATUSES)[number];

export const FLOOR_STATUS_LABELS: Record<FloorStatus, string> = {
  UNMAPPED: 'Not drawn',
  DRAFT: 'Draft, from photos',
  VERIFIED: 'Verified on site',
};

/**
 * Mirrors Floor. Identified by `code` - S1, P0, P1, MEZZ, T - because a mezzanine has no integer
 * level; `level` only orders the floors, so the mezzanine is 1.5.
 */
export interface Floor {
  code: string;
  level: number;
  name: string;
  status?: FloorStatus;
  accessibility?: Accessibility;
  note?: string | null;
  gridRows: number;
  gridColumns: number;
  corridors?: Corridor[];
  /** Read-only: bumped by every layout save. */
  version?: number;
}

/** Mirrors Wing: one arm of a building, in that building's own words. */
export interface Wing {
  code: string;
  name: string;
  /** What the doors of this wing append to the number - "-S" for 503-S. Absent when nothing. */
  doorSuffix?: string | null;
  note?: string | null;
}

/** Mirrors Building in docs/api/map.openapi.yaml. */
export interface Building {
  id: string;
  code: string;
  name: string;
  campus: string;
  description?: string;
  aliases: string[];
  wings: Wing[];
  floors: Floor[];
}

/** Mirrors BuildingRequest - the create/update payload. `id` is server-generated. */
export interface BuildingRequest {
  code: string;
  name: string;
  campus: string;
  description?: string;
  aliases: string[];
  wings: Wing[];
  floors: Floor[];
}

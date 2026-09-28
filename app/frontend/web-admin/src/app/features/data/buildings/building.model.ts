/**
 * Mirrors Point in docs/api/map.openapi.yaml: a point on a floor's drawing, in the floor's own
 * units. Origin at the top-left corner as the plan hangs; x to the right, y down.
 */
export interface Point {
  x: number;
  y: number;
}

/** Mirrors Corridor. */
export interface Corridor {
  code: string;
  name: string;
  color: string;
  path: Point[];
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
/** Mirrors Compass: a direction on the ground. */
export type Compass = 'NORTH' | 'EAST' | 'SOUTH' | 'WEST';

export interface Floor {
  code: string;
  level: number;
  name: string;
  status?: FloorStatus;
  accessibility?: Accessibility;
  note?: string | null;
  /** The drawing's size, in its own units; every point on the floor is within it. */
  width: number;
  height: number;
  /** The direction on the ground the drawing's top edge faces; absent until somebody says. */
  top?: Compass | null;
  /** The building's walls around the floor, corners in order. Empty until traced. */
  outline?: Point[];
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

/** Mirrors GeoPoint: a point on the earth, in degrees of WGS 84. */
export interface GeoPoint {
  lat: number;
  lon: number;
}

/**
 * Mirrors Placement: where a building's drawing lies on the ground - its top-left corner, the
 * bearing its top edge faces and how long one unit of it is. One per building: every floor shares
 * the building's drawing.
 */
export interface Placement {
  origin: GeoPoint;
  bearing: number;
  metresPerUnit: number;
}

/**
 * Mirrors FootprintPart: one part of a building as the city's cadastre records it from above - its
 * outline as `[lon, lat]` points, how many floors it rises and, when known, its wing.
 */
export interface FootprintPart {
  lot?: string | null;
  floors: number;
  basements: number;
  wing?: string | null;
  ring: [number, number][];
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
  /** Absent until somebody lays the building on the ground. */
  placement?: Placement | null;
  /** The building from above, part by part; empty until taken from the cadastre. */
  footprint?: FootprintPart[];
}

/**
 * Mirrors BuildingRequest - the create/update payload. `id` is server-generated. Leaving
 * `placement` or `footprint` out keeps the stored one.
 */
export interface BuildingRequest {
  code: string;
  name: string;
  campus: string;
  description?: string;
  aliases: string[];
  wings: Wing[];
  floors: Floor[];
  placement?: Placement | null;
  footprint?: FootprintPart[];
}

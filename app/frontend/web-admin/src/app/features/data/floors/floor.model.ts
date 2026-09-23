import type { Accessibility, Corridor, FloorStatus, Wing } from '../buildings/building.model';
import type { Space } from '../spaces/space.model';

/**
 * Mirrors FloorDetail in docs/api/map.openapi.yaml: one floor with every space on it, placed or
 * not, and the version a layout save has to send back.
 */
export interface FloorDetail {
  code: string;
  level: number;
  name: string;
  status: FloorStatus;
  accessibility: Accessibility;
  note?: string | null;
  gridRows: number;
  gridColumns: number;
  corridors?: Corridor[];
  version: number;
  buildingId: string;
  buildingCode: string;
  buildingName: string;
  campus: string;
  wings?: Wing[];
  spaces: Space[];
}

/** Mirrors LayoutSpace: a SpaceRequest minus the building and the floor, which the path names. */
export interface LayoutSpace {
  code: string;
  doorCode?: string | null;
  wing?: string | null;
  name: string;
  typeCode: string;
  aliases: string[];
  gridRow?: number | null;
  gridColumn?: number | null;
  rowSpan: number;
  colSpan: number;
  accessVia?: string | null;
  accessibility?: Accessibility | null;
  note?: string | null;
  capacity?: number | null;
}

/** Mirrors FloorLayoutRequest: the whole floor at once, refused with 409 if `version` is stale. */
export interface FloorLayoutRequest {
  version: number;
  gridRows: number;
  gridColumns: number;
  status: FloorStatus;
  accessibility: Accessibility;
  note?: string | null;
  corridors: Corridor[];
  spaces: LayoutSpace[];
}

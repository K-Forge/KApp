import type { Coordinate } from '../ground/ground.model';

/**
 * Mirrors Structure in docs/api/map.openapi.yaml: something on a campus's blocks that is not the
 * university's - a neighbour's building, a heritage house with its garden.
 */
export interface Structure {
  name: string;
  /** How many floors it rises; 0 for ground, like a garden. */
  floors: number;
  basements: number;
  lot?: string | null;
  /** Closed: the first point repeated at the end. */
  ring: Coordinate[];
}

/** Mirrors Structures: a campus's whole list, at the version it was read. */
export interface Structures {
  campus: string;
  structures: Structure[];
  version: number;
  updatedAt?: string | null;
}

/** Mirrors StructuresRequest: the whole list, with the version it was read at. */
export interface StructuresRequest {
  version: number;
  structures: Structure[];
}

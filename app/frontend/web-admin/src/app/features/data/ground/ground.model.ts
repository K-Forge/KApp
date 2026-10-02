/** A point as GeoJSON writes it: `[lon, lat]`, degrees of WGS 84. */
export type Coordinate = [number, number];

/** Mirrors Street: a street's name and the axis it is labelled along. */
export interface Street {
  name: string;
  /** As the street signs abbreviate it: "KR 9BIS". */
  label: string;
  path: Coordinate[];
}

/** Mirrors Lot: a cadastral lot - whose land is whose. */
export interface Lot {
  /** The block's nine digits, then the lot's three: 008213024019. */
  code: string;
  ring: Coordinate[];
}

/**
 * Mirrors Ground in docs/api/map.openapi.yaml: the city around a campus, from the city's
 * reference map. Each area is one closed ring.
 */
export interface Ground {
  campus: string;
  /** Where it comes from - credit it wherever it is shown. */
  source: string;
  retrieved: string;
  blocks: Coordinate[][];
  sidewalks: Coordinate[][];
  roadways: Coordinate[][];
  medians: Coordinate[][];
  streets: Street[];
  /** The lots of the blocks the university's buildings stand on; absent from older grounds. */
  lots?: Lot[];
}

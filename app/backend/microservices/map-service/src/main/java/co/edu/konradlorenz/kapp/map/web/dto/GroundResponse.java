package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The {@code Ground} schema: the city around a campus - its blocks, sidewalks, roadways and
 * streets, and the lots of the blocks the university stands on - taken from the city's own
 * reference map.
 *
 * <p>Coordinates are GeoJSON's: {@code [lon, lat]} in degrees of WGS 84. An area is one ring,
 * its first point repeated at the end.
 */
@Schema(name = "Ground", description = "The city around a campus, from the city's reference map. "
        + "Coordinates are [lon, lat] in WGS 84; an area is one closed ring.")
public record GroundResponse(
        @Schema(example = "Sede Principal") String campus,
        @Schema(description = "Where the data comes from, to credit wherever it is shown.") String source,
        @Schema(description = "The day it was taken from the source.", example = "2026-09-27") String retrieved,
        @ArraySchema(arraySchema = @Schema(description = "The city blocks (manzanas): the line where private land meets the sidewalk."))
        List<double[][]> blocks,
        @ArraySchema(arraySchema = @Schema(description = "The sidewalks (andenes)."))
        List<double[][]> sidewalks,
        @ArraySchema(arraySchema = @Schema(description = "The roadways (calzadas): where cars go."))
        List<double[][]> roadways,
        @ArraySchema(arraySchema = @Schema(description = "The medians (separadores) between roadways."))
        List<double[][]> medians,
        List<StreetDto> streets,
        @ArraySchema(arraySchema = @Schema(description = "The cadastral lots of the blocks the university's buildings "
                + "stand on: whose land is whose, for surveying a block."))
        List<LotDto> lots
) {

    public GroundResponse {
        lots = lots == null ? List.of() : lots;
    }

    /** A cadastral lot: its code and its outline. */
    @Schema(name = "Lot")
    public record LotDto(
            @Schema(description = "The cadastre's code: the block's nine digits, then the lot's three.",
                    example = "008213024019") String code,
            @Schema(description = "Its outline, [lon, lat] points, the first repeated at the end.") double[][] ring
    ) {
    }

    /** A street's name and the line it is labelled along. */
    @Schema(name = "Street")
    public record StreetDto(
            @Schema(example = "Calle 63") String name,
            @Schema(description = "The name as the street signs abbreviate it.", example = "CL 63") String label,
            @Schema(description = "The street's axis, [lon, lat] points.") double[][] path
    ) {
    }
}

package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The {@code Structure} schema: something on a campus's blocks that is not the university's.
 */
@Schema(name = "Structure", description = "Something on a campus's blocks that is not the university's - a "
        + "neighbour's building, a heritage house with its garden - drawn around the buildings so they read in "
        + "their place.")
public record StructureDto(
        @Schema(description = "What it is called, or what it is.", example = "Casa de Francisco de Paula Vélez")
        @NotBlank @Size(max = 120) String name,
        @Schema(description = "How many floors it rises above the street; 0 for ground, like a garden.", example = "1")
        @NotNull @Min(0) @Max(200) Integer floors,
        @Schema(description = "How many floors it goes below the street.", example = "0")
        @NotNull @Min(0) @Max(20) Integer basements,
        @Schema(description = "The cadastral lot it stands on, when known.", example = "008213024004")
        @Size(max = 30) String lot,
        @ArraySchema(arraySchema = @Schema(description = "Its outline, [lon, lat] points, the first repeated at the end."),
                minItems = 4)
        @NotNull @Size(min = 4, max = 2000) List<@NotNull @Size(min = 2, max = 2) List<@NotNull Double>> ring
) {
}

package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The {@code FootprintPart} schema: one part of a building from above, as the cadastre records it
 * or as it was found on site where the cadastre falls short.
 */
@Schema(name = "FootprintPart", description = "One part of a building from above, as the city's cadastre "
        + "records it or, where the cadastre falls short, as it was found on site: its outline on the ground "
        + "and how many floors it rises.")
public record FootprintPartDto(
        @Schema(description = "The cadastral lot the part stands on. Absent for a part the cadastre does not "
                + "record, found on site.", example = "008213024019")
        @Size(max = 30) String lot,
        @Schema(description = "How many floors it rises above the street: the cadastre's count, unless checked "
                + "on site.", example = "8")
        @NotNull @Min(0) @Max(200) Integer floors,
        @Schema(description = "The lowest floor above the street it takes in, when it is not the first: 2 for what "
                + "the upper floors carry out over a portico or a sidewalk, which the cadastre, seeing from above, "
                + "draws as if it reached the ground. Absent for a part that stands on the street.", example = "2")
        @Min(1) @Max(200) Integer lowestFloor,
        @Schema(description = "How many floors it goes below the street.", example = "2")
        @NotNull @Min(0) @Max(20) Integer basements,
        @Schema(description = "The code of the building's wing it belongs to, when known.", example = "C")
        @Size(max = 10) String wing,
        @ArraySchema(arraySchema = @Schema(description = "Its outline, [lon, lat] points, the first repeated at the end."),
                minItems = 4)
        @NotNull @Size(min = 4, max = 2000) List<@NotNull @Size(min = 2, max = 2) List<@NotNull Double>> ring
) {
}

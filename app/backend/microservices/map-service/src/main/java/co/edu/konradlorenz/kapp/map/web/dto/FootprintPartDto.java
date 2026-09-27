package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The {@code FootprintPart} schema: one part of a building as the cadastre records it from above. */
@Schema(name = "FootprintPart", description = "One part of a building as the city's cadastre records it from "
        + "above: its outline on the ground and how many floors it rises.")
public record FootprintPartDto(
        @Schema(description = "The cadastral lot the part stands on.", example = "008213024019")
        @Size(max = 30) String lot,
        @Schema(description = "How many floors it rises above the street.", example = "8")
        @NotNull @Min(0) @Max(200) Integer floors,
        @Schema(description = "How many floors it goes below the street.", example = "2")
        @NotNull @Min(0) @Max(20) Integer basements,
        @Schema(description = "The code of the building's wing it belongs to, when known.", example = "C")
        @Size(max = 10) String wing,
        @ArraySchema(arraySchema = @Schema(description = "Its outline, [lon, lat] points, the first repeated at the end."),
                minItems = 4)
        @NotNull @Size(min = 4, max = 2000) List<@NotNull @Size(min = 2, max = 2) List<@NotNull Double>> ring
) {
}

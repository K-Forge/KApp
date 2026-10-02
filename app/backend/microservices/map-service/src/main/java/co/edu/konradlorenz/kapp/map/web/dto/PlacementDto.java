package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** The {@code Placement} schema: where a building's drawing lies on the ground. */
@Schema(name = "Placement", description = "Where the building's drawing lies on the ground: its top-left "
        + "corner, the bearing its top edge faces and how long one unit of it is.")
public record PlacementDto(
        @Schema(description = "Where the drawing's top-left corner is.")
        @NotNull @Valid GeoPointDto origin,
        @Schema(description = "Degrees clockwise from true north that the drawing's top edge faces.",
                example = "127.8")
        @NotNull @DecimalMin("0") @DecimalMax(value = "360", inclusive = false) Double bearing,
        @Schema(description = "How long one unit of the drawing is on the ground, in metres.", example = "0.0295")
        @NotNull @Positive Double metresPerUnit
) {
}

package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "Point", description = "A point on a floor's drawing, in the floor's units. "
        + "Origin at the top-left corner as the plan hangs; x to the right, y down.")
public record PointDto(
        @Schema(example = "120") @NotNull @Min(0) Integer x,
        @Schema(example = "48") @NotNull @Min(0) Integer y
) {
}

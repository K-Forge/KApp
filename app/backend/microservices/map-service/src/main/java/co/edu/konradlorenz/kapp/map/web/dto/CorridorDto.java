package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "Corridor",
        description = "A walkable route across a floor, drawn as a coloured line.")
public record CorridorDto(
        @Schema(example = "PAS-CENTRAL") @NotBlank @Size(max = 30) String code,
        @Schema(example = "Pasillo central") @NotBlank @Size(max = 60) String name,
        @Schema(description = "CSS hex colour, matching what is painted on the actual walls where "
                + "the building colour-codes its wings.", example = "#F2A93B")
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
        @Schema(description = "The points the corridor runs through, in walking order. A polyline, "
                + "not a rectangle: real corridors bend.")
        @NotEmpty @Valid List<PointDto> path
) {
}

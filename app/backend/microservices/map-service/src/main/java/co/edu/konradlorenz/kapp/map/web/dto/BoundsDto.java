package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The rectangle around a shape, in the floor's units: where to put a label or a pin, and what to
 * frame when zooming to a space, without every client computing it from the corners.
 */
@Schema(name = "Bounds", description = "The rectangle around a space's shape, derived from it. Read-only.")
public record BoundsDto(
        @Schema(example = "120") int x,
        @Schema(example = "48") int y,
        @Schema(example = "96") int width,
        @Schema(example = "110") int height
) {
}

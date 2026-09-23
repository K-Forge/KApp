package co.edu.konradlorenz.kapp.map.web.dto;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One space, as {@code POST} and {@code PUT /api/map/spaces} write it.
 *
 * <p>The same fields travel inside a floor layout save as {@link LayoutSpaceDto}, minus the
 * building and the floor, which the layout's path already names.
 */
@Schema(name = "SpaceRequest")
public record SpaceRequest(
        @Schema(description = "Identifier within the building. For a numbered room it is the door "
                + "code itself; for a space with no number on its door, any stable code - it is "
                + "never shown.", example = "503-S")
        @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]*$",
                message = "letters, digits and dashes only")
        String code,

        @Schema(description = "Exactly as printed on the door. Omit it when the door has no number: "
                + "it is the only code a client ever shows.", example = "503-S")
        @Size(max = 20)
        String doorCode,

        @Schema(description = "Code of one of the building's wings.", example = "S")
        @Size(max = 8)
        String wing,

        @Schema(example = "Aula 503")
        @NotBlank @Size(max = 120)
        String name,

        @Schema(description = "A code from GET /api/map/space-types.", example = "CLASSROOM")
        @NotBlank @Size(max = 40)
        String typeCode,

        @Schema(description = "Code of the building that contains this space. Must already exist.",
                example = "EC")
        @NotBlank @Size(max = 10)
        String buildingCode,

        @Schema(description = "Code of the floor this space sits on. The building must already "
                + "have it.", example = "P5")
        @NotBlank @Size(max = 8)
        String floorCode,

        @Schema(description = "Other names people search for.")
        List<@NotBlank @Size(max = 120) String> aliases,

        @Schema(description = "Top-left cell, zero-based. Omit both gridRow and gridColumn for a "
                + "space that is known to be on the floor but not yet drawn.", example = "4")
        @Min(0) Integer gridRow,

        @Schema(example = "9")
        @Min(0) Integer gridColumn,

        @Schema(example = "1")
        @Min(1) @Max(60)
        Integer rowSpan,

        @Schema(example = "2")
        @Min(1) @Max(60)
        Integer colSpan,

        @Schema(description = "Code of the lift, staircase or entrance that serves this space.",
                example = "ASC-CENTRAL")
        @Size(max = 20)
        String accessVia,

        @Schema(description = "Omit to take the floor's value; set it where this space differs.")
        Accessibility accessibility,

        @Schema(description = "How to get there when accessVia is not enough.",
                example = "Solo por la escalera norte, desde el P5.")
        @Size(max = 300)
        String note,

        @Schema(description = "Seating capacity, where it is known.", example = "40")
        @Min(0)
        Integer capacity
) {

    public LayoutSpaceDto toLayoutSpace() {
        return new LayoutSpaceDto(code, doorCode, wing, name, typeCode, aliases, gridRow,
                gridColumn, rowSpan, colSpan, accessVia, accessibility, note, capacity);
    }
}

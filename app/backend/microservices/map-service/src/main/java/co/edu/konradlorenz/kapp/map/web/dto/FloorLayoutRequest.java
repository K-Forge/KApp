package co.edu.konradlorenz.kapp.map.web.dto;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import co.edu.konradlorenz.kapp.map.domain.FloorStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Everything drawn on one floor, saved at once: what the floor editor sends.
 *
 * <p>{@code spaces} is the complete list. A space of this floor that is missing from it is
 * deleted, one that is new is created, and the rest are replaced - all or nothing.
 *
 * @param version the floor's version as the editor loaded it. If anyone has saved the floor
 *                since, the save is refused with 409 rather than silently overwriting their work
 */
@Schema(name = "FloorLayoutRequest")
public record FloorLayoutRequest(
        @Schema(description = "The floor's version as it was loaded.", example = "3")
        @NotNull @Min(0)
        Long version,

        @NotNull @Min(1) @Max(60)
        Integer gridRows,

        @NotNull @Min(1) @Max(60)
        Integer gridColumns,

        FloorStatus status,

        Accessibility accessibility,

        @Size(max = 300)
        String note,

        @Valid List<CorridorDto> corridors,

        @Schema(description = "Every space on this floor, placed or not.")
        @NotNull @Size(max = 500)
        List<@NotNull @Valid LayoutSpaceDto> spaces
) {

    public List<CorridorDto> corridorsOrEmpty() {
        return corridors == null ? List.of() : corridors;
    }
}

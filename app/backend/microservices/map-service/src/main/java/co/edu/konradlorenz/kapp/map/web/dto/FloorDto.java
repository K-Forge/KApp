package co.edu.konradlorenz.kapp.map.web.dto;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import co.edu.konradlorenz.kapp.map.domain.FloorStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One floor, as the building editor writes it and as every read returns it.
 *
 * <p>{@code version} is read-only: it is returned so a client can send it back with a layout
 * save, and ignored when a whole building is written, because a floor's version belongs to the
 * layout endpoint alone.
 */
@Schema(name = "Floor",
        description = "One floor of a building, described as a grid the client draws rather than "
                + "as a plan image it overlays.")
public record FloorDto(
        @Schema(description = "Identifier within the building, and the path segment the API uses: "
                + "S1 for a basement, P0, P1, MEZZ for a mezzanine, T for a terrace.",
                example = "MEZZ")
        @NotBlank @Pattern(regexp = "^[A-Z0-9]{1,8}$",
                message = "must be 1 to 8 uppercase letters or digits, such as P1, S1 or MEZZ")
        String code,

        @Schema(description = "Vertical order only. Decimal so a mezzanine sits between the two "
                + "floors it is between: 1.5.", example = "1.5")
        @NotNull @DecimalMin("-5") @DecimalMax("99")
        Double level,

        @Schema(description = "Display name, in Spanish as shown to people.", example = "Mezzanine")
        @NotBlank @Size(max = 60)
        String name,

        @Schema(description = "UNMAPPED until anything is drawn, DRAFT when drawn from photographs "
                + "of posted plans, VERIFIED once walked. Defaults to UNMAPPED.")
        FloorStatus status,

        @Schema(description = "Whether the floor is reachable without stairs. Defaults to UNKNOWN, "
                + "which is never to be read as either answer.")
        Accessibility accessibility,

        @Schema(description = "How to get here when it is not obvious.",
                example = "Se sube por la escalera exterior.")
        @Size(max = 300)
        String note,

        @Schema(description = "Rows in this floor's grid.", example = "12")
        @NotNull @Min(1) @Max(60)
        Integer gridRows,

        @Schema(description = "Columns in this floor's grid.", example = "16")
        @NotNull @Min(1) @Max(60)
        Integer gridColumns,

        @Schema(description = "Walkable routes across this floor.")
        @Valid List<CorridorDto> corridors,

        @Schema(description = "Read-only. Bumped by every layout save; send it back with the next "
                + "one.", accessMode = Schema.AccessMode.READ_ONLY, example = "3")
        Long version
) {

    /** Never null, so callers do not have to keep checking. */
    public List<CorridorDto> corridorsOrEmpty() {
        return corridors == null ? List.of() : corridors;
    }
}

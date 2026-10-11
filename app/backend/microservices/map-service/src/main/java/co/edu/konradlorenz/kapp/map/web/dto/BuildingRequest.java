package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload for creating or replacing a building. {@code id} is server-generated and is
 * never accepted from the client, which is why it is absent here rather than ignored.
 *
 * <p>On {@code PUT} the {@code floors} and {@code wings} lists REPLACE the stored ones, so a
 * floor or a wing omitted here is deleted - and deleting one that still has spaces is refused
 * with 409.
 */
@Schema(name = "BuildingRequest")
public record BuildingRequest(

        @Schema(description = "Building code, unique across the map.", example = "EC")
        @NotBlank @Size(max = 10)
        String code,

        @Schema(example = "Edificio Central")
        @NotBlank @Size(max = 120)
        String name,

        @Schema(description = "The campus (Sede) this building belongs to.", example = "Sede Principal")
        @NotBlank @Size(max = 120)
        String campus,

        @Schema(description = "Optional free-text description shown on the building screen.")
        @Size(max = 500)
        String description,

        @Schema(description = "Other names people use for the building.")
        List<@NotBlank @Size(max = 120) String> aliases,

        @Schema(description = "The arms the building is divided into. Omit for a building with one.")
        List<@NotNull @Valid WingDto> wings,

        @Schema(description = "The complete list of floors. At least one.")
        @NotNull @NotEmpty
        List<@NotNull @Valid FloorDto> floors,
        @Schema(description = "Where the building's drawing lies on the ground. Omitted on PUT, the stored "
                + "one is kept.")
        @Valid PlacementDto placement,
        @Schema(description = "The building from above, part by part, as the city's cadastre records it. "
                + "Omitted on PUT, the stored one is kept.")
        List<@NotNull @Valid FootprintPartDto> footprint,
        @Schema(description = "Its street address as people write it. Omitted on PUT, the stored one is kept; "
                + "empty clears it.", example = "Cra. 9 Bis # 62-43")
        @Size(max = 200)
        String address,
        @Schema(description = "The names SINU gives this building as a sede, exactly as its timetable prints "
                + "them. Omitted on PUT, the stored ones are kept. A sede belongs to one building only; naming "
                + "one another building has is a 409.", example = "[\"Sede Principal\"]")
        List<@NotBlank @Size(max = 120) String> sinuSedes
) {
    /** A request that leaves the building's placement, footprint, address and sedes as they are. */
    public BuildingRequest(String code, String name, String campus, String description, List<String> aliases,
                           List<WingDto> wings, List<FloorDto> floors) {
        this(code, name, campus, description, aliases, wings, floors, null, null, null, null);
    }


    public List<String> aliasesOrEmpty() {
        return aliases == null ? List.of() : aliases;
    }

    public List<WingDto> wingsOrEmpty() {
        return wings == null ? List.of() : wings;
    }
}

package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The {@code StructuresRequest} schema: the whole list, as the portal saves it. */
@Schema(name = "StructuresRequest", description = "Everything on a campus's blocks that is not the university's, "
        + "the whole list, with the version it was read at.")
public record StructuresRequest(
        @Schema(description = "The version the list was read at; 0 while nothing has been saved.", example = "1")
        @NotNull Long version,
        @NotNull @Size(max = 500) List<@NotNull @Valid StructureDto> structures
) {
}

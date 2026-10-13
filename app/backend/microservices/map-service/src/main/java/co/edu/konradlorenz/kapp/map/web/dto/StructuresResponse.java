package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** The {@code Structures} schema: everything on a campus's blocks that is not the university's. */
@Schema(name = "Structures", description = "Everything on a campus's blocks that is not the university's.")
public record StructuresResponse(
        @Schema(example = "Sede Principal") String campus,
        List<StructureDto> structures,
        @Schema(description = "Bumped by every save. Send it back with the next one.", example = "1") long version,
        @Schema(description = "When it was last saved; absent while nothing has been.") Instant updatedAt
) {
}

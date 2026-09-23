package co.edu.konradlorenz.kapp.map.web.dto;

import co.edu.konradlorenz.kapp.map.domain.SpaceCategory;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SpaceType", description = "A kind of space in the catalogue the portal edits.")
public record SpaceTypeResponse(
        @Schema(example = "IT_ROOM") String code,
        @Schema(example = "Cuarto de TI") String name,
        SpaceCategory category
) {
}

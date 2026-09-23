package co.edu.konradlorenz.kapp.map.web.dto;

import co.edu.konradlorenz.kapp.map.domain.SpaceCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creates or renames a space type. On {@code PUT} the code in the path wins and the one here is
 * ignored: a type's code is what spaces store, so it never changes.
 */
@Schema(name = "SpaceTypeRequest")
public record SpaceTypeRequest(
        @Schema(example = "IT_ROOM")
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$",
                message = "must be 2 to 40 uppercase letters, digits or underscores, starting with a letter")
        String code,

        @Schema(example = "Cuarto de TI")
        @NotBlank @Size(max = 60)
        String name,

        @NotNull
        SpaceCategory category
) {
}

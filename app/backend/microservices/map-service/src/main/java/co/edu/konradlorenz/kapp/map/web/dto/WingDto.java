package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "Wing", description = "One arm of a building, as that building names it.")
public record WingDto(
        @Schema(example = "S")
        @NotBlank @Pattern(regexp = "^[A-Z0-9]{1,8}$",
                message = "must be 1 to 8 uppercase letters or digits")
        String code,

        @Schema(example = "Ala sur")
        @NotBlank @Size(max = 60)
        String name,

        @Schema(description = "What the doors in this wing append to the room number, or omitted "
                + "when they append nothing.", example = "-S")
        @Size(max = 4)
        String doorSuffix,

        @Schema(description = "How to get into or across this wing, where that is not obvious.",
                example = "Se cruza por el P1 o por la terraza.")
        @Size(max = 300)
        String note
) {
}

package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(name = "Door", description = "A way into a space: the stretch of its outline the door "
        + "takes up, jamb to jamb. Both ends lie on the space's shape.")
public record DoorDto(
        @NotNull @Valid PointDto from,
        @NotNull @Valid PointDto to
) {
}

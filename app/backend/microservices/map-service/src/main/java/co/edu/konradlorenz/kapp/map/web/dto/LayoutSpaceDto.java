package co.edu.konradlorenz.kapp.map.web.dto;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** A space inside a floor layout save. The floor and the building come from the path. */
@Schema(name = "LayoutSpace")
public record LayoutSpaceDto(
        @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]*$",
                message = "letters, digits and dashes only")
        String code,
        @Size(max = 20) String doorCode,
        @Size(max = 8) String wing,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 40) String typeCode,
        List<@NotBlank @Size(max = 120) String> aliases,
        @Schema(description = "The space's outline on its floor, corners in order, in the floor's "
                + "units. Omit it for a space known to be on the floor but not drawn yet.")
        @Size(min = 3, max = 200)
        List<@NotNull @Valid PointDto> shape,
        @Schema(description = "Its ways in, each on the shape's outline. Only with a shape.")
        @Size(max = 12)
        List<@NotNull @Valid DoorDto> doors,
        @Size(max = 20) String accessVia,
        Accessibility accessibility,
        @Size(max = 300) String note,
        @Min(0) Integer capacity
) {

    public List<DoorDto> doorsOrEmpty() {
        return doors == null ? List.of() : doors;
    }

    public List<String> aliasesOrEmpty() {
        return aliases == null ? List.of() : aliases;
    }

    public String doorCodeOrNull() {
        return doorCode == null || doorCode.isBlank() ? null : doorCode.trim();
    }

    public String wingOrNull() {
        return wing == null || wing.isBlank() ? null : wing.trim();
    }

    public String accessViaOrNull() {
        return accessVia == null || accessVia.isBlank() ? null : accessVia.trim();
    }
}

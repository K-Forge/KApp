package co.edu.konradlorenz.kapp.map.web.dto;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import co.edu.konradlorenz.kapp.map.domain.SpaceCategory;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The {@code SpaceDetail} schema: a space, the floor it sits on and a summary of its
 * building - everything the schedule screen needs to render a plan with a pin in ONE round
 * trip when a student taps a class.
 *
 * <p>The contract composes this with {@code allOf}, so the space's own fields are flat on
 * the object rather than nested under a key. They are therefore repeated here in the
 * contract's order rather than delegated to {@link SpaceResponse}: {@code @JsonUnwrapped}
 * would produce the same JSON but silently stops working the day this record needs to be
 * deserialised.
 */
@Schema(name = "SpaceDetail")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpaceDetailResponse(
        String id,
        String code,
        String doorCode,
        String baseCode,
        String wing,
        String name,
        String typeCode,
        String typeName,
        SpaceCategory category,
        String buildingId,
        String buildingCode,
        String campus,
        String floorCode,
        double floorLevel,
        List<String> aliases,
        @Schema(description = "Corners of the space's outline on its floor, in order. Absent while "
                + "the space is only inventoried.")
        List<PointDto> shape,
        @Schema(description = "The rectangle around the shape. Absent with it.", accessMode = Schema.AccessMode.READ_ONLY)
        BoundsDto bounds,
        @Schema(description = "Its ways in, each on the shape's outline. Empty until marked.")
        List<DoorDto> doors,
        String accessVia,
        Accessibility accessibility,
        Accessibility effectiveAccessibility,
        String note,
        Integer capacity,
        FloorDto floor,
        BuildingSummaryResponse building
) {
}

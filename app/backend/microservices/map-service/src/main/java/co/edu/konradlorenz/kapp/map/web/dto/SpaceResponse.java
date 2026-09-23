package co.edu.konradlorenz.kapp.map.web.dto;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import co.edu.konradlorenz.kapp.map.domain.SpaceCategory;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The {@code Space} schema.
 *
 * <p>{@code typeName} and {@code category} are copied from the type catalogue on the way out,
 * so a client can draw a space it has never seen the type of. {@code accessibility} is the
 * space's own value, null when it takes the floor's; {@code effectiveAccessibility} is the
 * answer to "can I get there without stairs?" either way.
 */
@Schema(name = "Space")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpaceResponse(
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
        Integer gridRow,
        Integer gridColumn,
        int rowSpan,
        int colSpan,
        String accessVia,
        Accessibility accessibility,
        Accessibility effectiveAccessibility,
        String note,
        Integer capacity
) {
}

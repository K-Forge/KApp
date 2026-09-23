package co.edu.konradlorenz.kapp.map.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The {@code BuildingSummary} schema: a building WITHOUT its floors.
 *
 * <p>Returned inside {@code SpaceDetail}, where the one floor that matters is already
 * included in full and the others would be dead weight on a mobile connection. The wings come
 * along because a space names its wing by code, and the client needs the name to show.
 */
@Schema(name = "BuildingSummary")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuildingSummaryResponse(
        String id,
        String code,
        String name,
        String campus,
        String description,
        List<String> aliases,
        List<WingDto> wings
) {
}

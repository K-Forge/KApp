package co.edu.konradlorenz.kapp.map.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The {@code Building} schema: a building with its wings and the full list of its floors,
 * ordered by ascending level.
 *
 * <p>{@code description} is optional in the contract, so a null one is omitted rather than
 * serialised as {@code null}.
 */
@Schema(name = "Building")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuildingResponse(
        String id,
        String code,
        String name,
        String campus,
        String description,
        List<String> aliases,
        List<WingDto> wings,
        List<FloorDto> floors
) {
}

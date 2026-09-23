package co.edu.konradlorenz.kapp.map.web.dto;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import co.edu.konradlorenz.kapp.map.domain.FloorStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Everything a client needs to draw one floor in a single call: the grid, the corridors that
 * cross it, the building's wings, and every space on it - placed or not.
 */
@Schema(name = "FloorDetail")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FloorDetailResponse(
        String code,
        double level,
        String name,
        FloorStatus status,
        Accessibility accessibility,
        String note,
        int gridRows,
        int gridColumns,
        List<CorridorDto> corridors,
        long version,
        String buildingId,
        String buildingCode,
        String buildingName,
        String campus,
        List<WingDto> wings,
        List<SpaceResponse> spaces
) {
}

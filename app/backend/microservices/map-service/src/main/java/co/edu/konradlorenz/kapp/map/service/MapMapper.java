package co.edu.konradlorenz.kapp.map.service;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.Corridor;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.GridPoint;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.domain.SpaceTypeDocument;
import co.edu.konradlorenz.kapp.map.domain.Wing;
import co.edu.konradlorenz.kapp.map.web.dto.BuildingResponse;
import co.edu.konradlorenz.kapp.map.web.dto.BuildingSummaryResponse;
import co.edu.konradlorenz.kapp.map.web.dto.CorridorDto;
import co.edu.konradlorenz.kapp.map.web.dto.FloorDetailResponse;
import co.edu.konradlorenz.kapp.map.web.dto.FloorDto;
import co.edu.konradlorenz.kapp.map.web.dto.GridPointDto;
import co.edu.konradlorenz.kapp.map.web.dto.SpaceDetailResponse;
import co.edu.konradlorenz.kapp.map.web.dto.SpaceResponse;
import co.edu.konradlorenz.kapp.map.web.dto.SpaceTypeResponse;
import co.edu.konradlorenz.kapp.map.web.dto.WingDto;

import java.util.List;

/**
 * Turns stored documents into the exact shapes {@code docs/api/map.openapi.yaml}
 * publishes.
 *
 * <p>The translation exists for one reason worth stating: documents carry a
 * {@code placeholder} flag that marks the invented seed campus, and that flag must never
 * reach a client. Returning documents straight from the controllers would publish it, and
 * would also make any future storage field an accidental addition to the API.
 */
public final class MapMapper {

    public static FloorDto toFloorDto(Floor floor) {
        return new FloorDto(floor.code(), floor.level(), floor.name(), floor.status(),
                floor.accessibility(), floor.note(), floor.gridRows(), floor.gridColumns(),
                floor.corridors().stream().map(MapMapper::toCorridorDto).toList(),
                floor.version());
    }

    /** @param version carried over from the stored floor: a floor's version is the layout's */
    public static Floor toFloor(FloorDto dto, long version) {
        return new Floor(dto.code(), dto.level(), dto.name(), dto.status(), dto.accessibility(),
                blankToNull(dto.note()), dto.gridRows(), dto.gridColumns(),
                dto.corridorsOrEmpty().stream().map(MapMapper::toCorridor).toList(), version);
    }

    public static WingDto toWingDto(Wing wing) {
        return new WingDto(wing.code(), wing.name(), wing.doorSuffix(), wing.note());
    }

    public static Wing toWing(WingDto dto) {
        return new Wing(dto.code(), dto.name(), blankToNull(dto.doorSuffix()), blankToNull(dto.note()));
    }

    public static CorridorDto toCorridorDto(Corridor corridor) {
        return new CorridorDto(corridor.code(), corridor.name(), corridor.color(),
                corridor.path().stream()
                        .map(point -> new GridPointDto(point.row(), point.col()))
                        .toList());
    }

    public static Corridor toCorridor(CorridorDto dto) {
        return new Corridor(dto.code(), dto.name(), dto.color(),
                dto.path().stream()
                        .map(point -> new GridPoint(point.row(), point.col()))
                        .toList());
    }

    public static BuildingResponse toBuildingResponse(BuildingDocument building) {
        return new BuildingResponse(
                building.id(),
                building.code(),
                building.name(),
                building.campus(),
                building.description(),
                building.aliases(),
                building.wings().stream().map(MapMapper::toWingDto).toList(),
                building.floors().stream().map(MapMapper::toFloorDto).toList());
    }

    public static BuildingSummaryResponse toBuildingSummary(BuildingDocument building) {
        return new BuildingSummaryResponse(
                building.id(),
                building.code(),
                building.name(),
                building.campus(),
                building.description(),
                building.aliases(),
                building.wings().stream().map(MapMapper::toWingDto).toList());
    }

    public static SpaceTypeResponse toSpaceTypeResponse(SpaceTypeDocument type) {
        return new SpaceTypeResponse(type.code(), type.name(), type.category());
    }

    /**
     * @param type  the space's type from the catalogue, or null if it has since been deleted -
     *              which the catalogue refuses while a space uses it, so null means a document
     *              written around the API
     * @param floor the space's floor, for the accessibility it inherits
     */
    public static SpaceResponse toSpaceResponse(SpaceDocument space, SpaceTypeDocument type, Floor floor) {
        return new SpaceResponse(
                space.id(),
                space.code(),
                space.doorCode(),
                space.baseCode(),
                space.wing(),
                space.name(),
                space.typeCode(),
                type == null ? null : type.name(),
                type == null ? null : type.category(),
                space.buildingId(),
                space.buildingCode(),
                space.campus(),
                space.floorCode(),
                space.floorLevel(),
                space.aliases(),
                space.gridRow(),
                space.gridColumn(),
                space.rowSpan(),
                space.colSpan(),
                space.accessVia(),
                space.accessibility(),
                effective(space, floor),
                space.note(),
                space.capacity());
    }

    public static SpaceDetailResponse toSpaceDetail(SpaceDocument space, SpaceTypeDocument type,
                                                    Floor floor, BuildingDocument building) {
        return new SpaceDetailResponse(
                space.id(),
                space.code(),
                space.doorCode(),
                space.baseCode(),
                space.wing(),
                space.name(),
                space.typeCode(),
                type == null ? null : type.name(),
                type == null ? null : type.category(),
                space.buildingId(),
                space.buildingCode(),
                space.campus(),
                space.floorCode(),
                space.floorLevel(),
                space.aliases(),
                space.gridRow(),
                space.gridColumn(),
                space.rowSpan(),
                space.colSpan(),
                space.accessVia(),
                space.accessibility(),
                effective(space, floor),
                space.note(),
                space.capacity(),
                toFloorDto(floor),
                toBuildingSummary(building));
    }

    public static FloorDetailResponse toFloorDetail(BuildingDocument building, Floor floor,
                                                    List<SpaceResponse> spaces) {
        return new FloorDetailResponse(
                floor.code(),
                floor.level(),
                floor.name(),
                floor.status(),
                floor.accessibility(),
                floor.note(),
                floor.gridRows(),
                floor.gridColumns(),
                floor.corridors().stream().map(MapMapper::toCorridorDto).toList(),
                floor.version(),
                building.id(),
                building.code(),
                building.name(),
                building.campus(),
                building.wings().stream().map(MapMapper::toWingDto).toList(),
                spaces);
    }

    private static Accessibility effective(SpaceDocument space, Floor floor) {
        if (space.accessibility() != null) {
            return space.accessibility();
        }
        return floor == null ? Accessibility.UNKNOWN : floor.accessibility();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MapMapper() {
    }
}

package co.edu.konradlorenz.kapp.map.service;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.Corridor;
import co.edu.konradlorenz.kapp.map.domain.Door;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.FootprintPart;
import co.edu.konradlorenz.kapp.map.domain.GeoPoint;
import co.edu.konradlorenz.kapp.map.domain.Placement;
import co.edu.konradlorenz.kapp.map.domain.Point;
import co.edu.konradlorenz.kapp.map.domain.Shape;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.domain.SpaceTypeDocument;
import co.edu.konradlorenz.kapp.map.domain.Wing;
import co.edu.konradlorenz.kapp.map.web.dto.BoundsDto;
import co.edu.konradlorenz.kapp.map.web.dto.BuildingResponse;
import co.edu.konradlorenz.kapp.map.web.dto.FootprintPartDto;
import co.edu.konradlorenz.kapp.map.web.dto.GeoPointDto;
import co.edu.konradlorenz.kapp.map.web.dto.PlacementDto;
import co.edu.konradlorenz.kapp.map.web.dto.BuildingSummaryResponse;
import co.edu.konradlorenz.kapp.map.web.dto.CorridorDto;
import co.edu.konradlorenz.kapp.map.web.dto.DoorDto;
import co.edu.konradlorenz.kapp.map.web.dto.FloorDetailResponse;
import co.edu.konradlorenz.kapp.map.web.dto.FloorDto;
import co.edu.konradlorenz.kapp.map.web.dto.PointDto;
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
                floor.accessibility(), floor.note(), floor.width(), floor.height(), floor.top(),
                toPointDtos(floor.outline()),
                floor.corridors().stream().map(MapMapper::toCorridorDto).toList(),
                floor.version());
    }

    /**
     * @param stored the floor as it is stored, or null for a new one. Its version is carried over -
     *               a floor's version is the layout's - and so is the direction its drawing faces,
     *               when the request leaves it out
     */
    public static Floor toFloor(FloorDto dto, Floor stored) {
        return new Floor(dto.code(), dto.level(), dto.name(), dto.status(), dto.accessibility(),
                blankToNull(dto.note()), dto.width(), dto.height(),
                dto.top() != null || stored == null ? dto.top() : stored.top(),
                toPoints(dto.outlineOrEmpty()),
                dto.corridorsOrEmpty().stream().map(MapMapper::toCorridor).toList(),
                stored == null ? 0 : stored.version());
    }

    public static WingDto toWingDto(Wing wing) {
        return new WingDto(wing.code(), wing.name(), wing.doorSuffix(), wing.note());
    }

    public static Wing toWing(WingDto dto) {
        return new Wing(dto.code(), dto.name(), blankToNull(dto.doorSuffix()), blankToNull(dto.note()));
    }

    public static CorridorDto toCorridorDto(Corridor corridor) {
        return new CorridorDto(corridor.code(), corridor.name(), corridor.color(),
                toPointDtos(corridor.path()));
    }

    public static Corridor toCorridor(CorridorDto dto) {
        return new Corridor(dto.code(), dto.name(), dto.color(),
                toPoints(dto.path()));
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
                building.floors().stream().map(MapMapper::toFloorDto).toList(),
                toPlacementDto(building.placement()),
                building.footprint().stream().map(MapMapper::toFootprintPartDto).toList(),
                building.address());
    }

    public static FootprintPartDto toFootprintPartDto(FootprintPart part) {
        return new FootprintPartDto(part.lot(), part.floors(), part.lowestFloor(), part.basements(), part.wing(), part.ring());
    }

    /** The footprint a request asks for, or the stored one when it names none. */
    public static List<FootprintPart> toFootprint(List<FootprintPartDto> dtos, List<FootprintPart> stored) {
        if (dtos == null) {
            return stored == null ? List.of() : stored;
        }
        return dtos.stream()
                .map(dto -> new FootprintPart(dto.lot(), dto.floors(), dto.lowestFloor(), dto.basements(),
                        dto.wing() == null || dto.wing().isBlank() ? null : dto.wing().trim(), dto.ring()))
                .toList();
    }

    public static PlacementDto toPlacementDto(Placement placement) {
        return placement == null ? null : new PlacementDto(
                new GeoPointDto(placement.origin().lat(), placement.origin().lon()),
                placement.bearing(), placement.metresPerUnit());
    }

    /** The placement a request asks for, or the stored one when it names none. */
    public static Placement toPlacement(PlacementDto dto, Placement stored) {
        return dto == null ? stored : new Placement(
                new GeoPoint(dto.origin().lat(), dto.origin().lon()), dto.bearing(), dto.metresPerUnit());
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
                toPointDtos(space.shape()),
                bounds(space.shape()),
                toDoorDtos(space.doors()),
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
                toPointDtos(space.shape()),
                bounds(space.shape()),
                toDoorDtos(space.doors()),
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
                floor.width(),
                floor.height(),
                floor.top(),
                toPointDtos(floor.outline()),
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

    /** @return the points as the API writes them, or null for no shape at all */
    public static List<PointDto> toPointDtos(List<Point> points) {
        return points == null ? null : points.stream().map(p -> new PointDto(p.x(), p.y())).toList();
    }

    public static List<DoorDto> toDoorDtos(List<Door> doors) {
        return doors.stream().map(d -> new DoorDto(new PointDto(d.from().x(), d.from().y()),
                new PointDto(d.to().x(), d.to().y()))).toList();
    }

    public static List<Door> toDoors(List<DoorDto> doors) {
        return doors.stream().map(d -> new Door(new Point(d.from().x(), d.from().y()),
                new Point(d.to().x(), d.to().y()))).toList();
    }

    /** @return the points as stored, or null for no shape at all */
    public static List<Point> toPoints(List<PointDto> points) {
        return points == null ? null : points.stream().map(p -> new Point(p.x(), p.y())).toList();
    }

    static BoundsDto bounds(List<Point> shape) {
        if (shape == null || shape.isEmpty()) {
            return null;
        }
        var box = Shape.bounds(shape);
        return new BoundsDto((int) box.getX(), (int) box.getY(), (int) box.getWidth(), (int) box.getHeight());
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MapMapper() {
    }
}

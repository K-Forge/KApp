package co.edu.konradlorenz.kapp.map.service;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.common.error.DuplicateResourceException;
import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.BuildingRepository;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.domain.SpaceRepository;
import co.edu.konradlorenz.kapp.map.domain.Wing;
import co.edu.konradlorenz.kapp.map.web.dto.BuildingRequest;
import co.edu.konradlorenz.kapp.map.web.dto.BuildingResponse;
import co.edu.konradlorenz.kapp.map.web.dto.CampusSummaryResponse;
import co.edu.konradlorenz.kapp.map.web.dto.FloorDetailResponse;
import co.edu.konradlorenz.kapp.map.web.dto.FloorDto;
import co.edu.konradlorenz.kapp.map.web.dto.WingDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Buildings, their wings and floors, and the campus list derived from them.
 *
 * <p>Buildings are addressed by {@code code} throughout, never by {@code id}: the code is
 * what is printed on the building and what a person types.
 */
@Service
public class BuildingService {

    private static final Logger log = LoggerFactory.getLogger(BuildingService.class);

    private final BuildingRepository buildings;
    private final SpaceRepository spaces;
    private final SpaceRendering rendering;

    public BuildingService(BuildingRepository buildings, SpaceRepository spaces, SpaceRendering rendering) {
        this.buildings = buildings;
        this.spaces = spaces;
        this.rendering = rendering;
    }

    /**
     * @param q matched against the code, the name and every alias, ignoring case and accents. A
     *          campus has a dozen buildings, so this is done in memory: a text index for twelve
     *          documents would cost more to maintain than it could ever save
     */
    public List<BuildingResponse> list(String campus, String q) {
        List<BuildingDocument> found = StringUtils.hasText(campus)
                ? buildings.findAllByCampusOrderByCodeAsc(campus)
                : buildings.findAllByOrderByCodeAsc();
        if (StringUtils.hasText(q)) {
            String needle = fold(q);
            found = found.stream()
                    .filter(b -> Stream.concat(Stream.of(b.code(), b.name()), b.aliases().stream())
                            .anyMatch(text -> fold(text).contains(needle)))
                    .toList();
        }
        return found.stream().map(MapMapper::toBuildingResponse).toList();
    }

    public BuildingResponse getByCode(String code) {
        return MapMapper.toBuildingResponse(require(code));
    }

    public BuildingResponse create(BuildingRequest request) {
        checkFloorsAndWings(request);

        if (buildings.existsByCode(request.code())) {
            throw new DuplicateResourceException("Building", request.code());
        }

        Instant now = Instant.now();
        BuildingDocument saved = buildings.save(new BuildingDocument(
                UUID.randomUUID().toString(),
                request.code(),
                request.name(),
                request.campus(),
                request.description(),
                cleanAliases(request.aliasesOrEmpty()),
                request.wingsOrEmpty().stream().map(MapMapper::toWing).toList(),
                request.floors().stream().map(floor -> MapMapper.toFloor(floor, 0)).toList(),
                false,
                now,
                now));

        log.info("Created building {} on campus {} with {} floors",
                saved.code(), saved.campus(), saved.floors().size());
        return MapMapper.toBuildingResponse(saved);
    }

    /**
     * Replaces a building, wings and floors included.
     *
     * <p>A floor or a wing may only disappear once nothing is on it, because the alternative is
     * orphaning every space a colleague placed there. Every copy a space carries of its building
     * - the code, the campus, its floor's level, the base code its wing's suffix produces - is
     * rewritten when the original changes; skipping that is how a search result comes back
     * claiming a building that no longer exists.
     *
     * <p>A floor keeps its version: that number belongs to the layout save, and bumping it here
     * would make an editor with the floor open refuse to save for no reason it could see.
     */
    public BuildingResponse update(String code, BuildingRequest request) {
        checkFloorsAndWings(request);

        BuildingDocument existing = require(code);

        if (!existing.code().equals(request.code()) && buildings.existsByCode(request.code())) {
            throw new DuplicateResourceException("Building", request.code());
        }

        List<Floor> floors = request.floors().stream()
                .map(dto -> MapMapper.toFloor(dto,
                        existing.floor(dto.code()).map(Floor::version).orElse(0L)))
                .toList();
        List<Wing> wings = request.wingsOrEmpty().stream().map(MapMapper::toWing).toList();
        rejectRemovingOccupied(existing, floors, wings);

        BuildingDocument saved = buildings.save(new BuildingDocument(
                existing.id(),
                request.code(),
                request.name(),
                request.campus(),
                request.description(),
                cleanAliases(request.aliasesOrEmpty()),
                wings,
                floors,
                existing.placeholder(),
                existing.createdAt(),
                Instant.now()));

        propagateToSpaces(saved);
        return MapMapper.toBuildingResponse(saved);
    }

    /**
     * Deletes an EMPTY building. A building that still holds spaces is refused with 409
     * rather than cascading: a cascade would take every hand-placed space with it on behalf of
     * someone who typed the wrong code.
     */
    public void delete(String code) {
        BuildingDocument existing = require(code);

        if (spaces.existsByBuildingId(existing.id())) {
            throw new MapConflictException(
                    "Building %s still has spaces and cannot be deleted. Delete its spaces first."
                            .formatted(existing.code()));
        }

        buildings.delete(existing);
        log.info("Deleted building {}", existing.code());
    }

    /** One floor and every space on it, placed or not - the call that draws a floor. */
    public FloorDetailResponse getFloor(String code, String floorCode) {
        BuildingDocument building = require(code);
        Floor floor = requireFloor(building, floorCode);
        List<SpaceDocument> onFloor =
                spaces.findByBuildingIdAndFloorCodeOrderByCodeAsc(building.id(), floorCode);
        return MapMapper.toFloorDetail(building, floor, rendering.render(onFloor, building));
    }

    /**
     * Every campus that has at least one building, with its building count.
     *
     * <p>Grouped in memory rather than with an aggregation on purpose: a university has a
     * handful of campuses and a few dozen buildings, so the pipeline would cost more to read
     * than it saves to run.
     */
    public List<CampusSummaryResponse> listCampuses() {
        Map<String, Long> byCampus = buildings.findAllByOrderByCodeAsc().stream()
                .collect(Collectors.groupingBy(BuildingDocument::campus,
                        LinkedHashMap::new, Collectors.counting()));

        return byCampus.entrySet().stream()
                .map(entry -> new CampusSummaryResponse(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CampusSummaryResponse::name))
                .toList();
    }

    /** Shared by the space and layout services, which need the building a space is put into. */
    BuildingDocument require(String code) {
        return buildings.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Building", code));
    }

    static Floor requireFloor(BuildingDocument building, String floorCode) {
        return building.floor(floorCode).orElseThrow(() -> new ResourceNotFoundException(
                "Floor %s of building %s".formatted(floorCode, building.code())));
    }

    /**
     * Two floors with one code, or at one level, and two wings with one code, are each a mistake
     * nobody could see on screen: the second floor would be unreachable by its code, and two
     * floors at one level would draw in whatever order the database felt like.
     */
    private void checkFloorsAndWings(BuildingRequest request) {
        List<ApiError.FieldIssue> issues = new ArrayList<>();
        Set<String> floorCodes = new HashSet<>();
        Set<Double> levels = new HashSet<>();
        for (FloorDto floor : request.floors()) {
            if (!floorCodes.add(floor.code())) {
                issues.add(new ApiError.FieldIssue("floors", "Duplicate floor code: " + floor.code()));
            }
            if (!levels.add(floor.level())) {
                issues.add(new ApiError.FieldIssue("floors", "Two floors at level " + floor.level()));
            }
            issues.addAll(SpaceRules.checkCorridors(floor.gridRows(), floor.gridColumns(),
                    floor.corridorsOrEmpty()).stream()
                    .map(i -> new ApiError.FieldIssue("floors[" + floor.code() + "]." + i.field(), i.issue()))
                    .toList());
        }
        Set<String> wingCodes = new HashSet<>();
        for (WingDto wing : request.wingsOrEmpty()) {
            if (!wingCodes.add(wing.code())) {
                issues.add(new ApiError.FieldIssue("wings", "Duplicate wing code: " + wing.code()));
            }
        }
        if (!issues.isEmpty()) {
            throw new BusinessRuleException("The building's floors or wings are inconsistent", issues);
        }
    }

    private void rejectRemovingOccupied(BuildingDocument existing, List<Floor> floors, List<Wing> wings) {
        Set<String> keptFloors = floors.stream().map(Floor::code).collect(Collectors.toSet());
        Set<String> keptWings = wings.stream().map(Wing::code).collect(Collectors.toSet());

        List<ApiError.FieldIssue> occupied = new ArrayList<>();
        existing.floors().stream()
                .map(Floor::code)
                .filter(floorCode -> !keptFloors.contains(floorCode))
                .filter(floorCode -> spaces.existsByBuildingIdAndFloorCode(existing.id(), floorCode))
                .forEach(floorCode -> occupied.add(new ApiError.FieldIssue("floors",
                        "Floor %s still has spaces on it".formatted(floorCode))));
        existing.wings().stream()
                .map(Wing::code)
                .filter(wingCode -> !keptWings.contains(wingCode))
                .filter(wingCode -> spaces.existsByBuildingIdAndWing(existing.id(), wingCode))
                .forEach(wingCode -> occupied.add(new ApiError.FieldIssue("wings",
                        "Wing %s still has spaces in it".formatted(wingCode))));

        if (!occupied.isEmpty()) {
            throw new MapConflictException(
                    "Building %s cannot drop a floor or a wing that still has spaces"
                            .formatted(existing.code()),
                    occupied);
        }
    }

    /**
     * Spaces carry copies of their building's code and campus, their floor's level and a base
     * code derived from their wing's door suffix, so that a search result needs no join. Those
     * copies are rewritten here, and only on the spaces where one of them actually changed.
     */
    private void propagateToSpaces(BuildingDocument building) {
        Map<String, Double> levels = new HashMap<>();
        building.floors().forEach(floor -> levels.put(floor.code(), floor.level()));

        List<SpaceDocument> affected = spaces.findByBuildingId(building.id()).stream()
                .filter(space -> !space.buildingCode().equals(building.code())
                        || !space.campus().equals(building.campus())
                        || space.floorLevel() != levels.getOrDefault(space.floorCode(), space.floorLevel())
                        || !Objects.equals(space.baseCode(),
                                SpaceDocument.baseCodeOf(space.doorCode(), building.wings())))
                .map(space -> new SpaceDocument(
                        space.id(), space.code(), space.doorCode(),
                        SpaceDocument.baseCodeOf(space.doorCode(), building.wings()),
                        space.wing(), space.name(), space.typeCode(), space.buildingId(),
                        building.code(), building.campus(), space.floorCode(),
                        levels.getOrDefault(space.floorCode(), space.floorLevel()),
                        space.aliases(), space.gridRow(), space.gridColumn(), space.rowSpan(),
                        space.colSpan(), space.accessVia(), space.accessibility(), space.note(),
                        space.capacity(), space.placeholder(), space.createdAt(), Instant.now()))
                .toList();

        if (!affected.isEmpty()) {
            spaces.saveAll(affected);
            log.info("Propagated building {} changes to {} spaces", building.code(), affected.size());
        }
    }

    private static List<String> cleanAliases(List<String> aliases) {
        return aliases.stream().map(String::trim).filter(a -> !a.isEmpty()).distinct().toList();
    }

    /** Lower case, no accents: "Bienestar" and "bienestar", "Psicología" and "psicologia". */
    static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}

package co.edu.konradlorenz.kapp.map.service;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.common.error.ConflictException;
import co.edu.konradlorenz.kapp.common.error.DuplicateResourceException;
import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.SpaceCategory;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.domain.SpaceRepository;
import co.edu.konradlorenz.kapp.map.domain.SpaceTypeDocument;
import co.edu.konradlorenz.kapp.map.web.dto.LayoutSpaceDto;
import co.edu.konradlorenz.kapp.map.web.dto.PageResponse;
import co.edu.konradlorenz.kapp.map.web.dto.SpaceDetailResponse;
import co.edu.konradlorenz.kapp.map.web.dto.SpaceRequest;
import co.edu.konradlorenz.kapp.map.web.dto.SpaceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spaces one at a time: the search, the room-code lookup the schedule screen depends on, and
 * the single-space writes the portal's forms use. A whole floor is saved by
 * {@link FloorLayoutService}, under the same {@link SpaceRules}.
 *
 * <p>A space is addressed by its {@code code}, the key {@code schedule-service} stores against a
 * class. The code is unique within a building but not across the map, so every lookup here can
 * come back ambiguous and says so rather than guessing.
 */
@Service
public class SpaceService {

    private static final Logger log = LoggerFactory.getLogger(SpaceService.class);

    private final SpaceRepository spaces;
    private final BuildingService buildingService;
    private final SpaceSearch search;
    private final SpaceRendering rendering;

    public SpaceService(SpaceRepository spaces, BuildingService buildingService, SpaceSearch search,
                        SpaceRendering rendering) {
        this.spaces = spaces;
        this.buildingService = buildingService;
        this.search = search;
        this.rendering = rendering;
    }

    /**
     * @param category narrows to the types in one category. Resolved here to the type codes it
     *                 currently holds, because spaces store the type, not the category - a type
     *                 moved between categories must not leave its spaces behind
     */
    public PageResponse<SpaceResponse> search(String q, String campus, String typeCode,
                                              SpaceCategory category, String buildingCode,
                                              String wing, String floorCode, int page, int size) {
        List<String> typeCodes = null;
        if (StringUtils.hasText(typeCode)) {
            typeCodes = List.of(typeCode);
        }
        if (category != null) {
            List<String> inCategory = rendering.typesByCode().values().stream()
                    .filter(type -> type.category() == category)
                    .map(SpaceTypeDocument::code)
                    .toList();
            typeCodes = typeCodes == null
                    ? inCategory
                    : typeCodes.stream().filter(inCategory::contains).toList();
            if (typeCodes.isEmpty()) {
                return PageResponse.of(List.of(), page, size, 0);
            }
        }
        SpaceSearch.Result result = search.search(
                new SpaceSearch.Filters(q, campus, typeCodes, buildingCode, wing, floorCode), page, size);
        return PageResponse.of(rendering.render(result.content()), page, size, result.totalElements());
    }

    /**
     * The endpoint a student reaches by tapping a class in their timetable: one round trip
     * from a room code to the space, its floor plan and its building.
     */
    public SpaceDetailResponse getDetail(String code, String buildingCode) {
        SpaceDocument space = resolve(code, buildingCode);
        BuildingDocument building = buildingService.require(space.buildingCode());
        Floor floor = BuildingService.requireFloor(building, space.floorCode());
        return MapMapper.toSpaceDetail(space, rendering.typesByCode().get(space.typeCode()), floor, building);
    }

    public SpaceResponse create(SpaceRequest request) {
        BuildingDocument building = buildingService.require(request.buildingCode());
        Floor floor = BuildingService.requireFloor(building, request.floorCode());

        if (spaces.findByBuildingIdAndCode(building.id(), request.code()).isPresent()) {
            throw new DuplicateResourceException(
                    "Space %s in building %s".formatted(request.code(), building.code()),
                    request.code());
        }
        check(building, floor, request.toLayoutSpace(), null);

        Instant now = Instant.now();
        SpaceDocument saved = spaces.save(toDocument(request.toLayoutSpace(), building, floor,
                UUID.randomUUID().toString(), false, now, now));

        log.info("Created space {} in building {} on floor {}",
                saved.code(), saved.buildingCode(), saved.floorCode());
        return rendering.render(List.of(saved), building).get(0);
    }

    /**
     * Replaces a space. Moving a room happens here too, by sending a new building, floor or
     * grid cell.
     */
    public SpaceResponse update(String code, String buildingCode, SpaceRequest request) {
        SpaceDocument current = resolve(code, buildingCode);
        BuildingDocument target = buildingService.require(request.buildingCode());
        Floor floor = BuildingService.requireFloor(target, request.floorCode());

        spaces.findByBuildingIdAndCode(target.id(), request.code())
                .filter(clash -> !clash.id().equals(current.id()))
                .ifPresent(clash -> {
                    throw new DuplicateResourceException(
                            "Space %s in building %s".formatted(request.code(), target.code()),
                            request.code());
                });
        check(target, floor, request.toLayoutSpace(), current.id());

        SpaceDocument saved = spaces.save(toDocument(request.toLayoutSpace(), target, floor,
                current.id(),
                // Provenance, not content: a corrected cell on an invented floor is still on
                // an invented floor. The flag clears when a real survey replaces the seed.
                current.placeholder(),
                current.createdAt(), Instant.now()));

        return rendering.render(List.of(saved), target).get(0);
    }

    /**
     * Deletes a space, unless another space names it as the way to get there - removing the
     * lift three classrooms point at would leave them saying "sube por" nothing.
     */
    public void delete(String code, String buildingCode) {
        SpaceDocument target = resolve(code, buildingCode);
        List<SpaceDocument> pointingAtIt = spaces.findByBuildingIdAndAccessVia(target.buildingId(), target.code());
        if (!pointingAtIt.isEmpty()) {
            throw new MapConflictException(
                    "Space %s is how %d other space(s) are reached. Point them elsewhere first."
                            .formatted(target.code(), pointingAtIt.size()),
                    pointingAtIt.stream()
                            .map(s -> new ApiError.FieldIssue("accessVia", s.code()))
                            .toList());
        }
        spaces.delete(target);
        log.info("Deleted space {} from building {}", target.code(), target.buildingCode());
    }

    /**
     * Resolves a code to exactly one space.
     *
     * <p>Three outcomes, and all three are in the contract: nothing matches (404), one
     * matches (the answer), or several buildings use the code and no disambiguator was
     * supplied (409, candidates named). The third is why {@code buildingCode} exists as a
     * query parameter instead of being baked into the path.
     */
    private SpaceDocument resolve(String code, String buildingCode) {
        List<SpaceDocument> matches = spaces.findByCodeOrderByBuildingCodeAsc(code);

        if (StringUtils.hasText(buildingCode)) {
            matches = matches.stream()
                    .filter(space -> space.buildingCode().equals(buildingCode))
                    .toList();
        }

        if (matches.isEmpty()) {
            throw new ResourceNotFoundException("Space", code);
        }
        if (matches.size() > 1) {
            throw new AmbiguousSpaceCodeException(code,
                    matches.stream().map(SpaceDocument::buildingCode).toList());
        }
        return matches.get(0);
    }

    /**
     * Runs {@link SpaceRules} over the floor as it would be with this space in it.
     *
     * <p>A mistake in the space itself is a 400. Landing on a cell another space already holds is
     * a 409: the request is fine, the floor's current state is what it collides with.
     *
     * @param replacing the id of the space being replaced, so an update does not collide with
     *                  itself; null on create
     */
    private void check(BuildingDocument building, Floor floor, LayoutSpaceDto candidate, String replacing) {
        List<SpaceDocument> inBuilding = spaces.findByBuildingId(building.id()).stream()
                .filter(space -> replacing == null || !space.id().equals(replacing))
                .toList();

        List<LayoutSpaceDto> onFloor = new ArrayList<>();
        onFloor.add(candidate);
        List<SpaceDocument> elsewhere = new ArrayList<>();
        for (SpaceDocument space : inBuilding) {
            if (space.floorCode().equals(floor.code())) {
                onFloor.add(SpaceRules.asLayout(space));
            } else {
                elsewhere.add(space);
            }
        }
        List<String> labels = new ArrayList<>(Collections.nCopies(onFloor.size(), ""));

        Map<String, SpaceTypeDocument> types = rendering.typesByCode();
        SpaceRules.Findings findings = SpaceRules.check(building, floor, onFloor, labels, Set.of(0),
                elsewhere, types);

        if (!findings.invalid().isEmpty()) {
            throw new BusinessRuleException("Space %s cannot be placed as sent".formatted(candidate.code()),
                    findings.invalid());
        }
        if (!findings.overlaps().isEmpty()) {
            throw new ConflictException("Space %s would overlap another space on floor %s of building %s"
                    .formatted(candidate.code(), floor.code(), building.code()), findings.overlaps());
        }
    }

    static SpaceDocument toDocument(LayoutSpaceDto space, BuildingDocument building, Floor floor,
                                    String id, boolean placeholder, Instant createdAt, Instant updatedAt) {
        String doorCode = space.doorCodeOrNull();
        return new SpaceDocument(
                id,
                space.code(),
                doorCode,
                SpaceDocument.baseCodeOf(doorCode, building.wings()),
                space.wingOrNull(),
                space.name().trim(),
                space.typeCode(),
                building.id(),
                building.code(),
                building.campus(),
                floor.code(),
                floor.level(),
                space.aliasesOrEmpty().stream().map(String::trim).filter(a -> !a.isEmpty()).distinct().toList(),
                space.gridRow(),
                space.gridColumn(),
                space.rowSpanOrOne(),
                space.colSpanOrOne(),
                space.accessViaOrNull(),
                space.accessibility(),
                MapMapper.blankToNull(space.note()),
                space.capacity(),
                placeholder,
                createdAt,
                updatedAt);
    }
}

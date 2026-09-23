package co.edu.konradlorenz.kapp.map.service;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.domain.SpaceRepository;
import co.edu.konradlorenz.kapp.map.web.dto.FloorDetailResponse;
import co.edu.konradlorenz.kapp.map.web.dto.FloorLayoutRequest;
import co.edu.konradlorenz.kapp.map.web.dto.LayoutSpaceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Saves everything drawn on one floor at once: what the floor editor sends when somebody presses
 * "Guardar" after walking the floor.
 *
 * <h2>All or nothing</h2>
 * A layout save moves, creates and deletes rooms and redraws corridors in one request, and it
 * runs in a transaction. Half of it applied would be worse than none: rooms moved but corridors
 * not, or a room deleted while the one that replaced it failed validation.
 *
 * <h2>Nobody overwrites anybody</h2>
 * The request carries the floor's version as the editor loaded it. If someone saved the floor in
 * between, the save is refused with 409 instead of silently replacing their work with a copy that
 * never saw it. The check is part of the update itself, so two saves arriving at once cannot
 * both pass it.
 */
@Service
public class FloorLayoutService {

    private static final Logger log = LoggerFactory.getLogger(FloorLayoutService.class);

    private final BuildingService buildings;
    private final SpaceRepository spaces;
    private final SpaceRendering rendering;
    private final MongoTemplate mongo;

    public FloorLayoutService(BuildingService buildings, SpaceRepository spaces,
                              SpaceRendering rendering, MongoTemplate mongo) {
        this.buildings = buildings;
        this.spaces = spaces;
        this.rendering = rendering;
        this.mongo = mongo;
    }

    @Transactional
    public FloorDetailResponse save(String buildingCode, String floorCode, FloorLayoutRequest request) {
        BuildingDocument building = buildings.require(buildingCode);
        Floor current = BuildingService.requireFloor(building, floorCode);

        if (current.version() != request.version()) {
            throw staleVersion(building, current, request.version());
        }

        Floor next = new Floor(current.code(), current.level(), current.name(),
                request.status() == null ? current.status() : request.status(),
                request.accessibility() == null ? current.accessibility() : request.accessibility(),
                MapMapper.blankToNull(request.note()),
                request.gridRows(), request.gridColumns(),
                request.corridorsOrEmpty().stream().map(MapMapper::toCorridor).toList(),
                current.version() + 1);

        List<SpaceDocument> inBuilding = spaces.findByBuildingId(building.id());
        Map<String, SpaceDocument> storedHere = inBuilding.stream()
                .filter(s -> s.floorCode().equals(floorCode))
                .collect(Collectors.toMap(SpaceDocument::code, Function.identity()));
        List<SpaceDocument> elsewhere = inBuilding.stream()
                .filter(s -> !s.floorCode().equals(floorCode))
                .toList();

        validate(building, next, request, elsewhere);

        Set<String> kept = request.spaces().stream().map(LayoutSpaceDto::code).collect(Collectors.toSet());
        List<SpaceDocument> removed = storedHere.values().stream()
                .filter(s -> !kept.contains(s.code()))
                .toList();
        rejectRemovingWhatOthersUse(removed, elsewhere);

        writeFloor(building, current, next);

        Instant now = Instant.now();
        List<SpaceDocument> toSave = request.spaces().stream()
                .map(space -> {
                    SpaceDocument before = storedHere.get(space.code());
                    return before == null
                            ? SpaceService.toDocument(space, building, next, UUID.randomUUID().toString(),
                                    false, now, now)
                            : SpaceService.toDocument(space, building, next, before.id(),
                                    before.placeholder(), before.createdAt(), now);
                })
                .toList();
        spaces.deleteAll(removed);
        spaces.saveAll(toSave);

        log.info("Saved floor {} of building {} at version {}: {} space(s), {} removed",
                floorCode, building.code(), next.version(), toSave.size(), removed.size());

        BuildingDocument after = buildings.require(buildingCode);
        return MapMapper.toFloorDetail(after, next, rendering.render(
                spaces.findByBuildingIdAndFloorCodeOrderByCodeAsc(building.id(), floorCode), after));
    }

    private void validate(BuildingDocument building, Floor floor, FloorLayoutRequest request,
                          List<SpaceDocument> elsewhere) {
        List<LayoutSpaceDto> sent = request.spaces();
        List<String> labels = IntStream.range(0, sent.size()).mapToObj(i -> "spaces[" + i + "]").toList();
        Set<Integer> all = new HashSet<>(IntStream.range(0, sent.size()).boxed().toList());

        SpaceRules.Findings findings = SpaceRules.check(building, floor, sent, labels, all,
                elsewhere, rendering.typesByCode());

        List<ApiError.FieldIssue> issues = new ArrayList<>(findings.invalid());
        // In a layout the overlapping rooms are both in the drawing being saved, so it is the
        // drawing that is wrong: a 400, not a conflict with anything stored.
        issues.addAll(findings.overlaps());
        issues.addAll(SpaceRules.checkCorridors(floor.gridRows(), floor.gridColumns(), request.corridorsOrEmpty()));

        if (!issues.isEmpty()) {
            throw new BusinessRuleException("The floor was not saved: %d problem(s). Nothing was changed."
                    .formatted(issues.size()), issues);
        }
    }

    /**
     * A space removed from this floor may be the lift or staircase rooms on other floors say to
     * take. Deleting it would leave them pointing at nothing, so the save is refused and the rooms
     * are named.
     */
    private void rejectRemovingWhatOthersUse(List<SpaceDocument> removed, List<SpaceDocument> elsewhere) {
        Set<String> removedCodes = removed.stream().map(SpaceDocument::code).collect(Collectors.toSet());
        List<ApiError.FieldIssue> dangling = elsewhere.stream()
                .filter(s -> s.accessVia() != null && removedCodes.contains(s.accessVia()))
                .map(s -> new ApiError.FieldIssue("spaces",
                        "%s on floor %s is reached through %s, which this save removes"
                                .formatted(s.code(), s.floorCode(), s.accessVia())))
                .toList();
        if (!dangling.isEmpty()) {
            throw new MapConflictException("The floor was not saved: it removes a space other floors use "
                    + "to get there. Nothing was changed.", dangling);
        }
    }

    /**
     * Replaces the one floor inside the building document, and only while it still has the
     * version that was checked. Touching just that array element, rather than saving the whole
     * building, is what keeps a save on floor 3 from undoing a save someone just made on floor 5.
     */
    private void writeFloor(BuildingDocument building, Floor current, Floor next) {
        Query query = Query.query(Criteria.where("_id").is(building.id())
                .and("floors").elemMatch(Criteria.where("code").is(current.code())
                        .and("version").is(current.version())));
        Update update = new Update()
                .set("floors.$.status", next.status())
                .set("floors.$.accessibility", next.accessibility())
                .set("floors.$.note", next.note())
                .set("floors.$.gridRows", next.gridRows())
                .set("floors.$.gridColumns", next.gridColumns())
                .set("floors.$.corridors", next.corridors())
                .set("floors.$.version", next.version())
                .set("updatedAt", Instant.now());

        if (mongo.updateFirst(query, update, BuildingDocument.class).getModifiedCount() == 0) {
            throw staleVersion(building, current, current.version());
        }
    }

    private static MapConflictException staleVersion(BuildingDocument building, Floor floor, long sent) {
        return new MapConflictException(
                "Floor %s of building %s was saved by someone else since you opened it. Reload it to see their changes."
                        .formatted(floor.code(), building.code()),
                List.of(new ApiError.FieldIssue("version",
                        "you sent %d, the floor is at %d".formatted(sent, floor.version()))));
    }
}

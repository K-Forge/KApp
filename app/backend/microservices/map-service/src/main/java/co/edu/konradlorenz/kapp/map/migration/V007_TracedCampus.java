package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.domain.Wing;
import co.edu.konradlorenz.kapp.map.service.MapMapper;
import co.edu.konradlorenz.kapp.map.service.SpaceService;
import co.edu.konradlorenz.kapp.map.web.dto.LayoutSpaceDto;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Loads the campus as the snapshot under {@code db/seed/map/} draws it: rooms traced from the
 * evacuation plans, in place of the grid drafts and of the placeholder campus.
 *
 * <p>The first drafts were drawn by eye on a grid and did not look like the plans. The snapshot
 * now carries each room's outline as the plan on the wall shows it, and this change unit puts it
 * in place of whatever nobody has worked on since.
 *
 * <h2>What is replaced, and what is kept</h2>
 * <ul>
 *   <li>Placeholder spaces go. A placeholder building goes too, unless somebody has since put a
 *       real space in it - then it stays, and the log says so.</li>
 *   <li>A building of the snapshot that does not exist yet is added whole.</li>
 *   <li>In one that does, each floor is replaced only if nobody has touched it: its
 *       {@code version} is still 0 - no layout was ever saved - and none of its spaces has been
 *       edited ({@code updatedAt} still equals {@code createdAt}). A floor somebody worked on is
 *       left exactly as it is, and named in the log.</li>
 *   <li>A replaced space keeps its id: the snapshot's version of a room is the same room.</li>
 *   <li>The building's own name, description and other names are taken from the snapshot only
 *       if the building was never edited either. Wings the snapshot declares and the building
 *       lacks are always added, since the snapshot's spaces may be in them.</li>
 * </ul>
 *
 * <p>Nothing it writes counts as an edit: floors stay at version 0 and spaces keep
 * {@code updatedAt} equal to {@code createdAt}, so the next snapshot can replace them again. Run
 * twice, it writes the same thing twice.
 *
 * <p>Tests turn it off with {@code kapp.map.survey-seed=false}, and run against the placeholder
 * campus instead; {@code TracedCampusTest} runs it on its own.
 *
 * <p>Change units are append-only. Never edit one that has run; add a new one.
 */
@ChangeUnit(id = "map-traced-campus-v007", order = "007", author = "kapp")
public class V007_TracedCampus {

    static final String ENABLED = "kapp.map.survey-seed";

    private static final Logger log = LoggerFactory.getLogger(V007_TracedCampus.class);

    @Execution
    public void execute(MongoTemplate mongo, Environment environment) {
        if (!environment.getProperty(ENABLED, Boolean.class, true)) {
            log.info("Traced campus not loaded: {} is false", ENABLED);
            return;
        }
        Result result = apply(mongo, SurveySnapshot.load(), Instant.now());
        log.info("Traced campus: {} building(s) added, {} floor(s) replaced, {} space(s) written; "
                        + "{} placeholder building(s) and {} placeholder space(s) removed",
                result.buildingsAdded(), result.floorsReplaced(), result.spacesWritten(),
                result.placeholderBuildingsRemoved(), result.placeholderSpacesRemoved());
        if (!result.kept().isEmpty()) {
            log.warn("Floors somebody worked on, left as they are: {}", result.kept());
        }
        if (!result.skipped().isEmpty()) {
            log.warn("Spaces not written because their code is on a floor that was kept: {}", result.skipped());
        }
        if (!result.placeholdersInUse().isEmpty()) {
            log.warn("Placeholder buildings kept because they hold real spaces: {}", result.placeholdersInUse());
        }
    }

    /**
     * Nothing to put back. What this replaced were drafts nobody had touched, and what it wrote
     * is a whole, valid campus; if it stops half way, running it again finishes the job.
     */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Nothing to undo.
    }

    /**
     * @param kept    floors left alone because somebody worked on them, as {@code EC P3}
     * @param skipped spaces of a replaced floor whose code is already used on a kept floor
     */
    record Result(int buildingsAdded, int floorsReplaced, int spacesWritten, long placeholderSpacesRemoved,
                  int placeholderBuildingsRemoved, List<String> kept, List<String> skipped,
                  List<String> placeholdersInUse) {
    }

    static Result apply(MongoTemplate mongo, List<SurveySnapshot.Building> snapshot, Instant now) {
        long placeholderSpaces = mongo.remove(query(where("placeholder").is(true)), SpaceDocument.class)
                .getDeletedCount();

        int placeholderBuildings = 0;
        List<String> inUse = new ArrayList<>();
        for (BuildingDocument placeholder : mongo.find(query(where("placeholder").is(true)), BuildingDocument.class)) {
            if (mongo.exists(query(where("buildingId").is(placeholder.id())), SpaceDocument.class)) {
                inUse.add(placeholder.code());
            } else {
                mongo.remove(placeholder);
                placeholderBuildings++;
            }
        }

        Tally tally = new Tally();
        for (SurveySnapshot.Building surveyed : snapshot) {
            BuildingDocument stored = mongo.findOne(query(where("code").is(surveyed.code())), BuildingDocument.class);
            if (stored == null) {
                add(mongo, surveyed, now, tally);
            } else {
                merge(mongo, surveyed, stored, tally);
            }
        }
        return new Result(tally.buildings, tally.floors, tally.spaces, placeholderSpaces, placeholderBuildings,
                tally.kept, tally.skipped, inUse);
    }

    private static final class Tally {
        int buildings;
        int floors;
        int spaces;
        final List<String> kept = new ArrayList<>();
        final List<String> skipped = new ArrayList<>();
    }

    private static void add(MongoTemplate mongo, SurveySnapshot.Building surveyed, Instant now, Tally tally) {
        BuildingDocument building = mongo.insert(surveyed.toDocument(now));
        List<SpaceDocument> documents = new ArrayList<>();
        for (SurveySnapshot.SnapshotFloor floor : surveyed.floors()) {
            Floor stored = building.floor(floor.code()).orElseThrow();
            floor.spaces().forEach(space -> documents.add(SpaceService.toDocument(
                    space, building, stored, UUID.randomUUID().toString(), false, now, now)));
        }
        mongo.insertAll(documents);
        tally.buildings++;
        tally.floors += surveyed.floors().size();
        tally.spaces += documents.size();
    }

    private static void merge(MongoTemplate mongo, SurveySnapshot.Building surveyed, BuildingDocument stored,
                              Tally tally) {
        boolean buildingUntouched = stored.updatedAt().equals(stored.createdAt());
        Map<String, Wing> wings = new LinkedHashMap<>();
        stored.wings().forEach(w -> wings.put(w.code(), w));
        surveyed.wings().stream().map(MapMapper::toWing).forEach(w -> wings.putIfAbsent(w.code(), w));

        Map<String, Floor> floors = new LinkedHashMap<>();
        stored.floors().forEach(f -> floors.put(f.code(), f));
        List<SpaceDocument> all = mongo.find(query(where("buildingId").is(stored.id())), SpaceDocument.class);

        List<SpaceDocument> writes = new ArrayList<>();
        List<SpaceDocument> removals = new ArrayList<>();
        for (SurveySnapshot.SnapshotFloor floor : surveyed.floors()) {
            List<SpaceDocument> onFloor = all.stream().filter(s -> s.floorCode().equals(floor.code())).toList();
            Floor current = floors.get(floor.code());
            if (current != null && !untouched(current, onFloor)) {
                tally.kept.add(stored.code() + " " + floor.code());
                continue;
            }
            Floor next = floor.toFloor();
            floors.put(next.code(), next);
            tally.floors++;

            Set<String> elsewhere = all.stream().filter(s -> !s.floorCode().equals(floor.code()))
                    .map(SpaceDocument::code).collect(Collectors.toSet());
            Map<String, SpaceDocument> previous = new LinkedHashMap<>();
            onFloor.forEach(s -> previous.put(s.code(), s));
            for (LayoutSpaceDto space : floor.spaces()) {
                if (elsewhere.contains(space.code())) {
                    tally.skipped.add(stored.code() + " " + floor.code() + " " + space.code());
                    continue;
                }
                SpaceDocument before = previous.remove(space.code());
                Instant created = before == null ? stored.createdAt() : before.createdAt();
                writes.add(new PendingSpace(space, next, before == null ? UUID.randomUUID().toString() : before.id(),
                        created).document(stored, wings));
            }
            removals.addAll(previous.values());
        }

        BuildingDocument merged = new BuildingDocument(stored.id(), stored.code(),
                buildingUntouched ? surveyed.name() : stored.name(),
                stored.campus(),
                buildingUntouched ? surveyed.description() : stored.description(),
                buildingUntouched ? surveyed.aliases() : stored.aliases(),
                List.copyOf(wings.values()), List.copyOf(floors.values()),
                false, stored.createdAt(), stored.updatedAt());
        mongo.save(merged);
        removals.forEach(mongo::remove);
        // Written after the building, so a space's wing is always one its building declares.
        writes.forEach(mongo::save);
        tally.spaces += writes.size();
    }

    /** A space of the snapshot, waiting for the building its wings and base code come from. */
    private record PendingSpace(LayoutSpaceDto space, Floor floor, String id, Instant created) {

        SpaceDocument document(BuildingDocument stored, Map<String, Wing> wings) {
            BuildingDocument withWings = new BuildingDocument(stored.id(), stored.code(), stored.name(),
                    stored.campus(), stored.description(), stored.aliases(), List.copyOf(wings.values()),
                    stored.floors(), false, stored.createdAt(), stored.updatedAt());
            return SpaceService.toDocument(space, withWings, floor, id, false, created, created);
        }
    }

    private static boolean untouched(Floor floor, List<SpaceDocument> spaces) {
        return floor.version() == 0 && spaces.stream().allMatch(s -> s.updatedAt().equals(s.createdAt()));
    }
}

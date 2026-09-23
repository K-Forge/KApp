package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.service.SpaceService;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Replaces the placeholder campus with the real one, as the campus survey found it.
 *
 * <p>The buildings come from {@link SurveySnapshot}: EC, CPC 1, JAAB, BI, MU and EA, with their
 * other names, wings and floors, and every space an information plaque lists - plus the lifts,
 * stairs and bathrooms the evacuation plans draw - in each floor's inventory. Nothing is placed on
 * a grid and every floor is {@code UNMAPPED}: which box is which is only known by walking the
 * floor, which is what the portal's floor editor is for.
 *
 * <h2>Only placeholders are replaced</h2>
 * <ul>
 *   <li>Placeholder spaces go. A placeholder building goes too, unless somebody has since put a
 *       real space in it - then it stays, and the log says so, rather than leave that space
 *       pointing at nothing.</li>
 *   <li>A building of the snapshot whose code already exists is left exactly as it is. Whoever
 *       created it, in the portal or by hand, knows more than a transcription of photos.</li>
 * </ul>
 *
 * <p>Tests turn it off with {@code kapp.map.survey-seed=false}: they run against the placeholder
 * campus, whose fixtures are made to exercise the model, and the snapshot changes every time the
 * campus is exported. {@code SurveyedCampusTest} runs it on its own.
 *
 * <p>Change units are append-only. Never edit one that has run; add a new one.
 */
@ChangeUnit(id = "map-surveyed-campus-v005", order = "005", author = "kapp")
public class V005_SurveyedCampus {

    static final String ENABLED = "kapp.map.survey-seed";

    private static final Logger log = LoggerFactory.getLogger(V005_SurveyedCampus.class);

    @Execution
    public void execute(MongoTemplate mongo, Environment environment) {
        if (!environment.getProperty(ENABLED, Boolean.class, true)) {
            log.info("Surveyed campus not loaded: {} is false", ENABLED);
            return;
        }
        Result result = apply(mongo, SurveySnapshot.load(), Instant.now());
        log.info("Surveyed campus: {} building(s) and {} space(s) added; {} placeholder building(s) "
                        + "and {} placeholder space(s) removed",
                result.buildingsAdded(), result.spacesAdded(),
                result.placeholderBuildingsRemoved(), result.placeholderSpacesRemoved());
        if (!result.kept().isEmpty()) {
            log.warn("Already present and left as they are: {}", result.kept());
        }
        if (!result.placeholdersInUse().isEmpty()) {
            log.warn("Placeholder buildings kept because they hold real spaces: {}", result.placeholdersInUse());
        }
    }

    /**
     * Takes back the surveyed buildings nobody has touched since: no edit to the building, no
     * floor saved, no space changed. One that has been worked on stays - it is somebody's work
     * now, not this change unit's. The placeholder campus is not put back.
     */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        for (SurveySnapshot.Building surveyed : SurveySnapshot.load()) {
            BuildingDocument stored = mongo.findOne(query(where("code").is(surveyed.code())), BuildingDocument.class);
            if (stored != null && untouched(mongo, stored)) {
                mongo.remove(query(where("buildingId").is(stored.id())), SpaceDocument.class);
                mongo.remove(stored);
            }
        }
    }

    record Result(int buildingsAdded, int spacesAdded, long placeholderSpacesRemoved,
                  int placeholderBuildingsRemoved, List<String> kept, List<String> placeholdersInUse) {
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

        int buildings = 0;
        int spaces = 0;
        List<String> kept = new ArrayList<>();
        for (SurveySnapshot.Building surveyed : snapshot) {
            if (mongo.exists(query(where("code").is(surveyed.code())), BuildingDocument.class)) {
                kept.add(surveyed.code());
                continue;
            }
            BuildingDocument building = mongo.insert(surveyed.toDocument(now));
            List<SpaceDocument> documents = new ArrayList<>();
            for (SurveySnapshot.SnapshotFloor floor : surveyed.floors()) {
                Floor stored = building.floor(floor.code()).orElseThrow();
                floor.spaces().forEach(space -> documents.add(SpaceService.toDocument(
                        space, building, stored, UUID.randomUUID().toString(), false, now, now)));
            }
            mongo.insertAll(documents);
            buildings++;
            spaces += documents.size();
        }
        return new Result(buildings, spaces, placeholderSpaces, placeholderBuildings, kept, inUse);
    }

    private static boolean untouched(MongoTemplate mongo, BuildingDocument building) {
        boolean buildingUntouched = building.updatedAt().equals(building.createdAt())
                && building.floors().stream().allMatch(f -> f.version() == 0);
        return buildingUntouched && mongo.find(query(where("buildingId").is(building.id())), SpaceDocument.class)
                .stream().allMatch(s -> s.updatedAt().equals(s.createdAt()));
    }
}

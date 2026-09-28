package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Puts the Edificio Central as the snapshot now draws it in place of the database's, once.
 *
 * <p>Its first and third floors were saved in the portal - rooms named, nothing moved - before
 * {@link V007_TracedCampus} recorded what it loads, so no load could tell those names from a
 * redraw, and it left both floors as they were. Saving them also touched the building, which a
 * load then read as an edit of its footprint, so the building kept an older one. Brian asked for
 * the new drawing over the names this once; from now on a load carries such changes onto the new
 * drawing by itself ({@link FloorMerge}).
 *
 * <p>What it replaces is not lost: each floor, with every space on it, is kept whole in
 * {@code map_replaced_floors} first ({@link V008_RedrawnCentralGroundFloor#redraw}). Then the
 * building takes the snapshot's placement and footprint, and the whole building is recorded as the
 * base the next load compares against.
 */
@ChangeUnit(id = "map-fitted-ec-v009", order = "009", author = "kapp")
public class V009_FittedCentralBuilding {

    static final String BUILDING = "EC";
    static final List<String> FLOORS = List.of("P1", "P3");

    private static final Logger log = LoggerFactory.getLogger(V009_FittedCentralBuilding.class);

    @Execution
    public void execute(MongoTemplate mongo, Environment environment) {
        if (!environment.getProperty(V007_TracedCampus.ENABLED, Boolean.class, true)) {
            log.info("{} not refitted: {} is false", BUILDING, V007_TracedCampus.ENABLED);
            return;
        }
        int kept = apply(mongo, SurveySnapshot.load(), Instant.now());
        log.info("{} {} redrawn from the snapshot, with its placement and footprint; the floors they replaced "
                + "and their {} space(s) are in {}", BUILDING, FLOORS, kept, V008_RedrawnCentralGroundFloor.REPLACED);
    }

    /** Nothing to put back automatically: what was replaced is in {@code map_replaced_floors}, whole. */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Nothing to undo.
    }

    /** @return how many spaces the replaced floors had, now kept aside */
    static int apply(MongoTemplate mongo, List<SurveySnapshot.Building> snapshot, Instant now) {
        SurveySnapshot.Building surveyed = snapshot.stream().filter(b -> b.code().equals(BUILDING))
                .findFirst().orElseThrow(() -> new IllegalStateException(BUILDING + " is not in the snapshot"));
        if (mongo.findOne(query(where("code").is(BUILDING)), BuildingDocument.class) == null) {
            return 0;
        }
        int kept = 0;
        for (String floor : FLOORS) {
            kept += V008_RedrawnCentralGroundFloor.redraw(mongo, snapshot, BUILDING, floor, now);
        }
        mongo.updateFirst(query(where("code").is(BUILDING)),
                new Update().set("placement", surveyed.toPlacement()).set("footprint", surveyed.toFootprint()),
                BuildingDocument.class);
        SnapshotBases.record(mongo, surveyed, SnapshotBases.allFloors(surveyed));
        return kept;
    }
}

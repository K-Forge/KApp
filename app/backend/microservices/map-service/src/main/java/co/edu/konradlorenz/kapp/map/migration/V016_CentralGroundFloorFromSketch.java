package co.edu.konradlorenz.kapp.map.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Puts the first floor of the Edificio Central as Brian sketched it, on 2026-10-21, in place of the
 * database's, once.
 *
 * <p>He outlined every space of the floor by hand over its evacuation plan
 * (scripts/plan-tracing/specs/ec-p1-strokes.json), and the snapshot now draws it so: one cafeteria
 * with its north extension, the kitchen, the shop, the bathrooms, the lifts, the porter's lodge and
 * the patio beside the building. The floor in the database is one somebody saved, so no load would
 * replace it: names would be carried onto the old drawing instead ({@link FloorMerge}). He asked for
 * the new drawing in its place, as {@link V008_RedrawnCentralGroundFloor} did for the plan's.
 *
 * <p>What it replaces is not lost: the floor as it was, with every one of its spaces, is kept whole in
 * {@code map_replaced_floors} first.
 */
@ChangeUnit(id = "map-ec-p1-from-sketch-v016", order = "016", author = "kapp")
public class V016_CentralGroundFloorFromSketch {

    static final String BUILDING = "EC";
    static final String FLOOR = "P1";

    private static final Logger log = LoggerFactory.getLogger(V016_CentralGroundFloorFromSketch.class);

    @Execution
    public void execute(MongoTemplate mongo, Environment environment) {
        if (!environment.getProperty(V007_TracedCampus.ENABLED, Boolean.class, true)) {
            log.info("{} {} not redrawn from the sketch: {} is false", BUILDING, FLOOR, V007_TracedCampus.ENABLED);
            return;
        }
        int kept = apply(mongo, SurveySnapshot.load(), Instant.now());
        log.info("{} {} redrawn from Brian's sketch; the floor it replaced and its {} space(s) are in {}",
                BUILDING, FLOOR, kept, V008_RedrawnCentralGroundFloor.REPLACED);
    }

    /** Nothing to put back automatically: the floor it replaced is in {@code map_replaced_floors}, whole. */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Nothing to undo.
    }

    /** @return how many spaces the replaced floor had, now kept aside; 0 when it is not there yet */
    static int apply(MongoTemplate mongo, List<SurveySnapshot.Building> snapshot, Instant now) {
        return V008_RedrawnCentralGroundFloor.redraw(mongo, snapshot, BUILDING, FLOOR, now);
    }
}

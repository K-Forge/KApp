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
 * Puts the first floor of the Edificio Central with its auditorium turned, in place of the
 * database's, once.
 *
 * <p>Brian found the auditorium drawn as trapezoids (#218). Its own evacuation plan, laid over the
 * floor's, shows a block turned along the Carrera 9 Bis, and the snapshot now draws it so, with the
 * floor's outline and the passage to the Carrera 9A beside the Casa de Francisco de Paula Vélez. A
 * floor nobody saved since {@link V016_CentralGroundFloorFromSketch} is redrawn by
 * {@link V007_TracedCampus} anyway; one somebody saved would only have their changes merged onto
 * the new drawing, and he asked for the drawing itself, as V016 did.
 *
 * <p>What it replaces is not lost: the floor as it was, with every one of its spaces, is kept whole in
 * {@code map_replaced_floors} first.
 */
@ChangeUnit(id = "map-ec-p1-turned-auditorium-v017", order = "017", author = "kapp")
public class V017_TurnedAuditorium {

    static final String BUILDING = "EC";
    static final String FLOOR = "P1";

    private static final Logger log = LoggerFactory.getLogger(V017_TurnedAuditorium.class);

    @Execution
    public void execute(MongoTemplate mongo, Environment environment) {
        if (!environment.getProperty(V007_TracedCampus.ENABLED, Boolean.class, true)) {
            log.info("{} {} not redrawn with its auditorium turned: {} is false", BUILDING, FLOOR,
                    V007_TracedCampus.ENABLED);
            return;
        }
        int kept = apply(mongo, SurveySnapshot.load(), Instant.now());
        log.info("{} {} redrawn with its auditorium turned; the floor it replaced and its {} space(s) are in {}",
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

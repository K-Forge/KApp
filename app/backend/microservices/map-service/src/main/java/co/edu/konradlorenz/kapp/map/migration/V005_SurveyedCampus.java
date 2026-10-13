package co.edu.konradlorenz.kapp.map.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * *** SUPERSEDED BY {@code V007_TracedCampus}. DELIBERATELY DOES NOTHING. ***
 *
 * <p>This change unit loaded the surveyed campus from a snapshot drawn on a grid of cells. The
 * map is now drawn in polygons, the snapshot is written in them, and this body could no longer
 * read it.
 *
 * <p>It is emptied rather than rewritten, and rather than deleted, for the reason
 * {@code V002_PlaceholderCampusSeed} gives: Mongock has recorded this id as executed wherever it
 * ran and will never run it again there. {@code V006_PolygonShapes} converts what it wrote, and
 * {@code V007_TracedCampus} loads the snapshot - the same work, so an existing database and a
 * fresh one arrive at the same campus.
 */
@ChangeUnit(id = "map-surveyed-campus-v005", order = "005", author = "kapp")
public class V005_SurveyedCampus {

    @Execution
    public void execute(MongoTemplate mongo) {
        // Intentionally empty. See the class javadoc.
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Nothing to undo.
    }
}

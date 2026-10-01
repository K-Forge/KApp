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
 * The casa's south corner as Brian knows it: where the brick wall from the Tienda K meets the
 * house, an old door stands set in, and the balcony comes out almost to the sidewalk; V012 drew
 * that front as one straight line. This puts the seed's outline on a campus whose casa is still the
 * one V012 left, and leaves one corrected in the portal alone.
 */
@ChangeUnit(id = "map-casa-door-and-balcony-v013", order = "013", author = "kapp")
public class V013_CasaDoorAndBalcony {

    private static final Logger log = LoggerFactory.getLogger(V013_CasaDoorAndBalcony.class);

    /** The casa's ring as V012 drew it, straight along Cra 9A. */
    static final List<List<Double>> FROM_V012 = List.of(
            List.of(-74.0619934, 4.6484248),
            List.of(-74.061879, 4.6485713),
            List.of(-74.0618563, 4.6485745),
            List.of(-74.0616291, 4.6483992),
            List.of(-74.0617927, 4.6482656),
            List.of(-74.0619934, 4.6484248));

    @Execution
    public void execute(MongoTemplate mongo, Environment environment) {
        if (!environment.getProperty(V007_TracedCampus.ENABLED, Boolean.class, true)) {
            log.info("Casa not redrawn: {} is false", V007_TracedCampus.ENABLED);
            return;
        }
        log.info("Casa's door and balcony: {} campus list(s) redrawn",
                V012_CasaOnItsWall.redraw(mongo, V010_CampusStructures.load(), Instant.now(), FROM_V012));
    }

    /** Nothing to undo: the outline it replaced is still V012's to put back. */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Nothing to undo.
    }
}

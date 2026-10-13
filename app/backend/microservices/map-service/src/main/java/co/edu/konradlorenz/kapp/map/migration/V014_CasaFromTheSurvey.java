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
 * The casa as the finished survey has it. Its front on Cra 9A is 24.5 m: the old door, 3.77 m and
 * 1.47 m in, at the corner where the brick wall from the Tienda K ends, then the balcony and the
 * garden wall, 20.73 m. V013 had put that corner four metres too far north. Its south side now runs
 * along the Tienda K's wall. On a campus whose casa is still V013's; one corrected in the portal is
 * left alone.
 */
@ChangeUnit(id = "map-casa-from-the-survey-v014", order = "014", author = "kapp")
public class V014_CasaFromTheSurvey {

    private static final Logger log = LoggerFactory.getLogger(V014_CasaFromTheSurvey.class);

    /** The casa's ring as V013 drew it. */
    static final List<List<Double>> FROM_V013 = List.of(
            List.of(-74.0619812, 4.6484152),
            List.of(-74.0619667, 4.6484336),
            List.of(-74.061979, 4.6484433),
            List.of(-74.061879, 4.6485713),
            List.of(-74.0618563, 4.6485745),
            List.of(-74.0616291, 4.6483992),
            List.of(-74.0617927, 4.6482656),
            List.of(-74.0619812, 4.6484152));

    @Execution
    public void execute(MongoTemplate mongo, Environment environment) {
        if (!environment.getProperty(V007_TracedCampus.ENABLED, Boolean.class, true)) {
            log.info("Casa not redrawn: {} is false", V007_TracedCampus.ENABLED);
            return;
        }
        log.info("Casa from the survey: {} campus list(s) redrawn",
                V012_CasaOnItsWall.redraw(mongo, V010_CampusStructures.load(), Instant.now(), FROM_V013));
    }

    /** Nothing to undo: the outline it replaced is still V013's to put back. */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Nothing to undo.
    }
}

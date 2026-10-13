package co.edu.konradlorenz.kapp.map.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

/**
 * Maps SINU's "Sede Principal" to the Edificio Central, once.
 *
 * <p>In SINU each building is a sede, and a timetable names a class's room by its sede and its
 * number. Every class of a real 2026-2 timetable report said "Sede Principal", in rooms 302, 612 and
 * 708 to 711, all of them in the Edificio Central. The other buildings' sedes are not known yet:
 * they are mapped in the portal as they turn up.
 *
 * <p>Only while the Edificio Central has no sede and no other building has this one: a mapping
 * somebody made in the portal is theirs.
 */
@ChangeUnit(id = "map-central-building-sede-v015", order = "015", author = "kapp")
public class V015_CentralBuildingSede {

    private static final Logger log = LoggerFactory.getLogger(V015_CentralBuildingSede.class);

    static final String BUILDING = "EC";
    static final String SEDE = "Sede Principal";

    @Execution
    public void execute(MongoTemplate mongo, Environment environment) {
        if (!environment.getProperty(V007_TracedCampus.ENABLED, Boolean.class, true)) {
            log.info("No sede mapped: {} is false", V007_TracedCampus.ENABLED);
            return;
        }
        log.info("SINU's {} mapped to building {}: {}", SEDE, BUILDING, map(mongo) ? "yes" : "no, it was mapped already");
    }

    /** Nothing to undo: a sede somebody has since mapped is theirs. */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Nothing to undo.
    }

    /** @return whether the sede was mapped now */
    public static boolean map(MongoTemplate mongo) {
        if (mongo.exists(Query.query(Criteria.where("sinuSedes").is(SEDE)), "buildings")) {
            return false;
        }
        Query unmapped = Query.query(Criteria.where("code").is(BUILDING)
                .orOperator(Criteria.where("sinuSedes").exists(false), Criteria.where("sinuSedes").size(0)));
        return mongo.updateFirst(unmapped, Update.update("sinuSedes", List.of(SEDE)), "buildings")
                .getModifiedCount() == 1;
    }
}

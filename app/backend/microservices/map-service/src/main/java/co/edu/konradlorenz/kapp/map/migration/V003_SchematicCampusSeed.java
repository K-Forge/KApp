package co.edu.konradlorenz.kapp.map.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * *** SUPERSEDED BY {@code V004_MapModelV2}. DELIBERATELY DOES NOTHING. ***
 *
 * <p>This change unit seeded the first schematic placeholder campus: floors addressed by an
 * integer level, wings from a closed enum, a space type enum. The survey of the real campus
 * outgrew all three - a mezzanine has no integer level, Bienestar's wings are not north and south,
 * and new kinds of room keep turning up - so the documents it wrote cannot be expressed in the
 * current model.
 *
 * <p>It is emptied rather than rewritten, and rather than deleted, for the reason
 * {@code V002_PlaceholderCampusSeed} was: Mongock has recorded this id as executed on every
 * database that ran it and will never run it there again, so a rewritten body would only ever
 * run on fresh databases, and deleting the class would leave that record pointing at nothing.
 * {@code V004_MapModelV2} converts what this one wrote where it ran, and seeds the current
 * placeholder campus everywhere, so both kinds of database converge.
 */
@ChangeUnit(id = "map-schematic-campus-seed-v003", order = "003", author = "kapp")
public class V003_SchematicCampusSeed {

    @Execution
    public void execute(MongoTemplate mongo) {
        // Intentionally empty. See the class javadoc.
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Nothing to undo.
    }
}

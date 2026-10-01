package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.CampusStructuresDocument;
import co.edu.konradlorenz.kapp.map.domain.Structure;
import co.edu.konradlorenz.kapp.map.service.GroundService;
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

/**
 * The Casa de Francisco de Paula Vélez on its own wall. V010 drew it from its cadastre lot, whose
 * edge slants up to two metres inside the house's front and has a notch on its south side; Brian
 * measured the front straight along Cra 9A, the house almost on the sidewalk and its garden wall
 * after it. The seed now draws it so, and this puts that on a campus whose casa is still the one
 * V010 seeded - one somebody has corrected in the portal is theirs.
 */
@ChangeUnit(id = "map-casa-on-its-wall-v012", order = "012", author = "kapp")
public class V012_CasaOnItsWall {

    private static final Logger log = LoggerFactory.getLogger(V012_CasaOnItsWall.class);

    static final String CASA_LOT = "008213024004";

    /** The casa's ring as V010 seeded it, from the cadastre. */
    static final List<List<Double>> SEEDED = List.of(
            List.of(-74.0619278, 4.6484731),
            List.of(-74.0619771, 4.6484108),
            List.of(-74.0619588, 4.6483962),
            List.of(-74.0619498, 4.6484075),
            List.of(-74.0618002, 4.6482883),
            List.of(-74.0618097, 4.6482774),
            List.of(-74.0617927, 4.6482656),
            List.of(-74.0617927, 4.6482655),
            List.of(-74.0616291, 4.6483992),
            List.of(-74.061858, 4.6485762),
            List.of(-74.0619278, 4.6484731));

    @Execution
    public void execute(MongoTemplate mongo, Environment environment) {
        if (!environment.getProperty(V007_TracedCampus.ENABLED, Boolean.class, true)) {
            log.info("Casa not redrawn: {} is false", V007_TracedCampus.ENABLED);
            return;
        }
        log.info("Casa on its wall: {} campus list(s) redrawn", redraw(mongo, V010_CampusStructures.load(), Instant.now()));
    }

    /** Nothing to undo: the outline it replaced was the cadastre's, which is still in V010's history. */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Nothing to undo.
    }

    /** @return how many campus lists had their casa redrawn */
    static int redraw(MongoTemplate mongo, List<V010_CampusStructures.Seed> seeds, Instant now) {
        return redraw(mongo, seeds, now, SEEDED);
    }

    /**
     * The seed's casa on every campus whose casa is still {@code replacing}, the outline an earlier
     * change unit put there; one corrected in the portal since is left alone.
     *
     * @return how many campus lists had their casa redrawn
     */
    static int redraw(MongoTemplate mongo, List<V010_CampusStructures.Seed> seeds, Instant now,
                      List<List<Double>> replacing) {
        int redrawn = 0;
        for (V010_CampusStructures.Seed seed : seeds) {
            Structure wanted = seed.structures().stream().filter(s -> CASA_LOT.equals(s.lot())).findFirst().orElse(null);
            CampusStructuresDocument stored = mongo.findById(GroundService.key(seed.campus()), CampusStructuresDocument.class);
            if (wanted == null || stored == null) {
                continue;
            }
            List<Structure> structures = new ArrayList<>();
            boolean changed = false;
            for (Structure s : stored.structures()) {
                if (CASA_LOT.equals(s.lot()) && replacing.equals(s.ring())) {
                    structures.add(new Structure(s.name(), s.floors(), s.basements(), s.lot(), wanted.ring()));
                    changed = true;
                } else {
                    structures.add(s);
                }
            }
            if (changed) {
                mongo.save(new CampusStructuresDocument(stored.id(), stored.campus(), structures, stored.version() + 1, now));
                redrawn++;
            }
        }
        return redrawn;
    }
}

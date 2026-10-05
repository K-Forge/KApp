package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.CampusStructuresDocument;
import co.edu.konradlorenz.kapp.map.domain.Structure;
import co.edu.konradlorenz.kapp.map.service.GroundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts on each campus, once, what else stands on its blocks, as {@code db/seed/structures/} has it.
 *
 * <p>For the Sede Principal that is the Edificio Central's block: the Casa de Francisco de Paula
 * Vélez and its garden (lot 004), a heritage house the university roofed and keeps, and the
 * neighbours' building on the Calle 62 (lot 001), taken from the city's cadastre. From then on
 * they are the portal's to correct while the block is surveyed, so a campus that has a list keeps
 * it: this never writes over one.
 */
@ChangeUnit(id = "map-campus-structures-v010", order = "010", author = "kapp")
public class V010_CampusStructures {

    static final String PATTERN = "classpath*:db/seed/structures/*.json";

    private static final Logger log = LoggerFactory.getLogger(V010_CampusStructures.class);

    /** One campus's list, as the seed file writes it. */
    record Seed(String campus, List<Structure> structures) {
    }

    @Execution
    public void execute(MongoTemplate mongo, Environment environment) {
        if (!environment.getProperty(V007_TracedCampus.ENABLED, Boolean.class, true)) {
            log.info("Campus structures not seeded: {} is false", V007_TracedCampus.ENABLED);
            return;
        }
        List<String> added = seed(mongo, load(), Instant.now());
        log.info("Campus structures: {} campus list(s) added {}", added.size(), added);
    }

    /** Nothing to undo: a list somebody has since corrected is theirs. */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Nothing to undo.
    }

    /** @return the campuses given a list; one that had a list keeps it */
    static List<String> seed(MongoTemplate mongo, List<Seed> seeds, Instant now) {
        List<String> added = new ArrayList<>();
        for (Seed seed : seeds) {
            String id = GroundService.key(seed.campus());
            if (mongo.findById(id, CampusStructuresDocument.class) != null) {
                continue;
            }
            mongo.insert(new CampusStructuresDocument(id, seed.campus(), seed.structures(), 1, now));
            added.add(seed.campus());
        }
        return added;
    }

    static List<Seed> load() {
        ObjectMapper json = new ObjectMapper();
        List<Seed> seeds = new ArrayList<>();
        try {
            for (Resource file : new PathMatchingResourcePatternResolver().getResources(PATTERN)) {
                try (InputStream in = file.getInputStream()) {
                    seeds.add(json.readValue(in, Seed.class));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The seed files under db/seed/structures/ could not be read", e);
        }
        return seeds;
    }
}

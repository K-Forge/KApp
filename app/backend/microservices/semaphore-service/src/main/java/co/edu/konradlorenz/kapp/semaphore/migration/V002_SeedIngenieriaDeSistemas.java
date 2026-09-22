package co.edu.konradlorenz.kapp.semaphore.migration;

import co.edu.konradlorenz.kapp.semaphore.domain.Pensum;
import co.edu.konradlorenz.kapp.semaphore.domain.Program;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Seeds the Ingenieria de Sistemas catalogue so the MVP demos without an admin UI.
 *
 * <p>There is no admin front end in the MVP, so without this change unit the semaforo is
 * an empty grid on every fresh database and the four mobile clients have nothing to draw.
 *
 * <h2>The seed is a reconstruction, and says so</h2>
 * Only the items confirmed by the examples in {@code docs/api/semaphore.openapi.yaml}
 * carry a real institutional code, and those are the only ones with a non-null
 * {@code sinuCode}. Everything else uses a generated {@code IS-*} slug so that nobody can
 * mistake a reconstruction for institutional data. The declared header totals - 142
 * credits and 194 weekly hours - are kept exactly as the printed plan states them even
 * though the seeded items do not sum to them, because a semaforo that quietly lies is worse
 * than one that reports a discrepancy.
 *
 * <p>{@code V006_SeedThePublishedPensums} replaces this reconstruction with the printed plan.
 *
 * <p>Change units are append-only. Never edit one that has run; add a new one.
 */
@ChangeUnit(id = "semaphore-seed-ingenieria-sistemas-v002", order = "002", author = "kapp")
public class V002_SeedIngenieriaDeSistemas {

    private static final Logger log = LoggerFactory.getLogger(V002_SeedIngenieriaDeSistemas.class);

    private static final String PROGRAMS_RESOURCE = "db/seed/programs.json";
    private static final String PENSUM_RESOURCE = "db/seed/pensum-1015.json";

    static final String SEEDED_PROGRAM_CODE = "506";
    static final String SEEDED_PENSUM_CODE = "1015";

    private final ObjectMapper mapper = new ObjectMapper();

    @Execution
    public void execute(MongoTemplate mongo) {
        List<Program> programs = readList(PROGRAMS_RESOURCE, Program[].class);
        Pensum pensum = read(PENSUM_RESOURCE, Pensum.class);

        // Idempotent by hand rather than by upsert: a change unit runs once, but a
        // developer who drops the changelog collection without dropping the data would
        // otherwise get duplicate-key failures instead of a clean no-op.
        programs.forEach(program -> {
            if (!mongo.exists(queryById("_id", program.code()), Program.class)) {
                mongo.insert(program);
            }
        });
        if (!mongo.exists(queryById("_id", pensum.pensumCode()), Pensum.class)) {
            mongo.insert(pensum);
        }

        log.info("Seeded {} program(s) and pensum {} with {} items",
                programs.size(), pensum.pensumCode(), pensum.courses().size());
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        mongo.remove(queryById("_id", SEEDED_PENSUM_CODE), Pensum.class);
        mongo.remove(queryById("_id", SEEDED_PROGRAM_CODE), Program.class);
    }

    private static Query queryById(String field, String value) {
        return Query.query(Criteria.where(field).is(value));
    }

    private <T> T read(String resource, Class<T> type) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return mapper.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read seed resource " + resource, e);
        }
    }

    private <T> List<T> readList(String resource, Class<T[]> arrayType) {
        return List.of(read(resource, arrayType));
    }
}

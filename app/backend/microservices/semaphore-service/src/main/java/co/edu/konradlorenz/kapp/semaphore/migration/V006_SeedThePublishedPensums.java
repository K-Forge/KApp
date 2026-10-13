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
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Loads the 23 pensums the university publishes, and replaces the reconstructed 1015.
 *
 * <p>The documents are the plans Konrad Lorenz hands out as PDFs. {@code docs/pensums/} keeps
 * the transcription of each one, the tools that read them and the list of everything the PDFs
 * could not answer; the JSON under {@code db/seed/pensums/} is generated from there by
 * {@code docs/pensums/tools/build_seed.py} and is not edited by hand.
 *
 * <h2>The reconstructed 1015 is replaced, anything else is left alone</h2>
 * {@code V002} seeded Ingeniería de Sistemas from a drawing: 48 items, most of them under
 * invented {@code IS-*} codes. The printed grid has 51 items, every one with its code. A 1015
 * that still carries an {@code IS-*} item is that reconstruction and is overwritten. A pensum
 * that exists without that marker was created or imported by someone, and a migration has no
 * business deciding it is wrong: it is kept, and the log says so.
 *
 * <p>Progress already pinned to the reconstruction survives the swap. {@code PensumReconciler}
 * keeps every item the pensum no longer declares and reports it, so a passed grade is never
 * dropped; the student's semaforo shows it under the removed items until it is resolved.
 *
 * <p>Change units are append-only. Never edit one that has run; add a new one.
 */
@ChangeUnit(id = "semaphore-seed-published-pensums-v006", order = "006", author = "kapp")
public class V006_SeedThePublishedPensums {

    private static final Logger log = LoggerFactory.getLogger(V006_SeedThePublishedPensums.class);

    static final String PROGRAMS_RESOURCE = "db/seed/pensums/programs.json";
    static final String PENSUMS_PATTERN = "classpath*:db/seed/pensums/pensum-*.json";
    private static final String RECONSTRUCTION_RESOURCE = "db/seed/pensum-1015.json";
    private static final String RECONSTRUCTED_PENSUM = "1015";
    private static final String RECONSTRUCTION_MARKER = "IS-";

    private final ObjectMapper mapper = new ObjectMapper();

    @Execution
    public void execute(MongoTemplate mongo) {
        int programsAdded = 0;
        for (Program program : readPrograms()) {
            if (mongo.findById(program.code(), Program.class) == null) {
                mongo.insert(program);
                programsAdded++;
            }
        }

        int added = 0;
        int replaced = 0;
        List<String> kept = new ArrayList<>();
        for (Pensum pensum : readPensums()) {
            Pensum existing = mongo.findById(pensum.pensumCode(), Pensum.class);
            if (existing == null) {
                mongo.insert(pensum);
                added++;
            } else if (isReconstruction(existing)) {
                mongo.save(pensum);
                replaced++;
            } else {
                kept.add(pensum.pensumCode());
            }
        }

        log.info("Published pensums: {} program(s) and {} pensum(s) added, {} reconstruction(s) replaced",
                programsAdded, added, replaced);
        if (!kept.isEmpty()) {
            log.warn("Published pensums already present and left as they are: {}", kept);
        }
    }

    /**
     * Takes back what {@link #execute} added and puts the reconstruction back in place of 1015.
     * Program 506 predates this change unit, so it stays.
     */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        for (Pensum pensum : readPensums()) {
            if (!pensum.pensumCode().equals(RECONSTRUCTED_PENSUM)) {
                mongo.remove(pensum);
            }
        }
        for (Program program : readPrograms()) {
            if (!program.code().equals(V002_SeedIngenieriaDeSistemas.SEEDED_PROGRAM_CODE)
                    && mongo.findById(program.code(), Program.class) != null) {
                mongo.remove(program);
            }
        }
        mongo.save(read(new ClassPathResource(RECONSTRUCTION_RESOURCE), Pensum.class));
    }

    static boolean isReconstruction(Pensum pensum) {
        return pensum.pensumCode().equals(RECONSTRUCTED_PENSUM)
                && pensum.courses().stream()
                        .anyMatch(c -> c.pensumItemCode().startsWith(RECONSTRUCTION_MARKER));
    }

    List<Program> readPrograms() {
        return Arrays.asList(read(new ClassPathResource(PROGRAMS_RESOURCE), Program[].class));
    }

    List<Pensum> readPensums() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(PENSUMS_PATTERN);
            if (resources.length == 0) {
                throw new IllegalStateException("No published pensums found at " + PENSUMS_PATTERN);
            }
            return Arrays.stream(resources).map(r -> read(r, Pensum.class)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + PENSUMS_PATTERN, e);
        }
    }

    private <T> T read(Resource resource, Class<T> type) {
        try (InputStream in = resource.getInputStream()) {
            return mapper.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read seed resource " + resource.getDescription(), e);
        }
    }
}

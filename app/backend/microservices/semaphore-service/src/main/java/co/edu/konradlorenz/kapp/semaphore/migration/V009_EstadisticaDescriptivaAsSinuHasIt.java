package co.edu.konradlorenz.kapp.semaphore.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Gives Estadística Descriptiva, in pensum 1015, the code SINU has for it: 17080. The 2019 grid
 * prints 17018, and a real 2026-2 timetable report says 17080. The item keeps 17018 as its
 * {@code pensumItemCode}, so the plans and prerequisites that name it do not change; only the code a
 * client shows, and the one a SINU record is matched by, moves.
 *
 * <p>The seed carries 17080 from {@code docs/pensums/catalog.yaml}, so a fresh database is right from
 * the start; this is for the ones that already ran {@code V006}. Only while the stored code is still
 * the printed one: a code somebody has since set is theirs.
 */
@ChangeUnit(id = "semaphore-estadistica-descriptiva-sinu-code-v009", order = "009", author = "kapp")
public class V009_EstadisticaDescriptivaAsSinuHasIt {

    private static final Logger log = LoggerFactory.getLogger(V009_EstadisticaDescriptivaAsSinuHasIt.class);

    static final String PENSUM = "1015";
    static final String ITEM = "17018";
    static final String PRINTED = "17018";
    static final String SINU = "17080";

    @Execution
    public void execute(MongoTemplate mongo) {
        log.info("Estadística Descriptiva of pensum {} carries SINU's code {}: {}", PENSUM, SINU,
                set(mongo, PRINTED, SINU) ? "now" : "already, or the item is not there");
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        set(mongo, SINU, PRINTED);
    }

    static boolean set(MongoTemplate mongo, String from, String to) {
        Document item = new Document("pensumItemCode", ITEM).append("sinuCode", from);
        return mongo.getCollection("pensums").updateOne(
                new Document("_id", PENSUM).append("courses", new Document("$elemMatch", item)),
                new Document("$set", new Document("courses.$.sinuCode", to))).getModifiedCount() == 1;
    }
}

package co.edu.konradlorenz.kapp.semaphore.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Takes the printed {@code code} off every stored pensum item: semaphore 2.0 addresses an item by
 * its {@code pensumItemCode} and shows its {@code sinuCode}, and keeps no third code. On nineteen of
 * the twenty-three plans that field held a code KApp made up ({@code MKT-101}), which is exactly
 * what must never reach a student's screen looking like the university's own.
 *
 * <p>Prerequisites named items by that code. In every published plan it equalled the item code, so
 * they are unchanged; one that names a code an item of the same pensum had is rewritten to that
 * item's code, in case a pensum was edited by hand to make them differ.
 *
 * <p>Works on the raw documents, because the domain no longer has the field. Running it twice does
 * nothing the second time. Change units are append-only: never edit one that has run; add a new one.
 */
@ChangeUnit(id = "semaphore-pensum-items-without-printed-code-v008", order = "008", author = "kapp")
public class V008_PensumItemsWithoutPrintedCode {

    private static final Logger log = LoggerFactory.getLogger(V008_PensumItemsWithoutPrintedCode.class);

    @Execution
    public void execute(MongoTemplate mongo) {
        List<Object> rewritten = new ArrayList<>();
        for (Document pensum : mongo.getCollection("pensums").find()) {
            List<Document> courses = pensum.getList("courses", Document.class, List.of());
            Map<String, String> itemOfCode = new HashMap<>();
            courses.stream()
                    .filter(c -> c.getString("code") != null)
                    .forEach(c -> itemOfCode.put(c.getString("code"), c.getString("pensumItemCode")));

            boolean changed = false;
            List<Document> next = new ArrayList<>(courses.size());
            for (Document course : courses) {
                Document copy = new Document(course);
                changed |= copy.remove("code") != null || course.containsKey("code");
                List<String> prerequisites = course.getList("prerequisites", String.class, List.of());
                List<String> byItem = prerequisites.stream().map(p -> itemOfCode.getOrDefault(p, p)).toList();
                if (!byItem.equals(prerequisites)) {
                    copy.put("prerequisites", byItem);
                    changed = true;
                }
                next.add(copy);
            }
            if (changed) {
                mongo.getCollection("pensums").updateOne(new Document("_id", pensum.get("_id")),
                        new Document("$set", new Document("courses", next)));
                rewritten.add(pensum.get("_id"));
            }
        }
        log.info("Printed codes taken off the items of {} pensums: {}", rewritten.size(), rewritten);
    }

    /** Nothing to undo: wherever a printed code existed, the item code says the same. */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Nothing to undo.
    }
}

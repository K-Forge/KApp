package co.edu.konradlorenz.kapp.semaphore.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Names each placement of the students' plans by {@code pensumItemCode}, as semaphore 2.0 does, and
 * gives it the {@code electiveSinuCode} a slot may now carry - null, since no plan chose one before.
 *
 * <p>A placement was stored under {@code code}, which held the item code for a fixed course and an
 * elective slot alike, so the value carries over as it is. Running it twice does nothing the second
 * time. Change units are append-only: never edit one that has run; add a new one.
 */
@ChangeUnit(id = "semaphore-placements-by-pensum-item-code-v010", order = "010", author = "kapp")
public class V010_PlacementsByPensumItemCode {

    private static final Logger log = LoggerFactory.getLogger(V010_PlacementsByPensumItemCode.class);

    @Execution
    public void execute(MongoTemplate mongo) {
        int rewritten = 0;
        for (Document plan : mongo.getCollection("academicPlans").find(new Document("placements.code",
                new Document("$exists", true)))) {
            List<Document> placements = new ArrayList<>();
            for (Document placement : plan.getList("placements", Document.class, List.of())) {
                String item = placement.getString("pensumItemCode") != null
                        ? placement.getString("pensumItemCode")
                        : placement.getString("code");
                placements.add(new Document("pensumItemCode", item)
                        .append("plannedLevel", placement.get("plannedLevel"))
                        .append("electiveSinuCode", placement.get("electiveSinuCode")));
            }
            mongo.getCollection("academicPlans").updateOne(new Document("_id", plan.get("_id")),
                    new Document("$set", new Document("placements", placements)));
            rewritten++;
        }
        log.info("Placements named by pensumItemCode in {} plans", rewritten);
    }

    /** Puts the placements back under {@code code}, without a chosen elective. */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        for (Document plan : mongo.getCollection("academicPlans").find(new Document("placements.pensumItemCode",
                new Document("$exists", true)))) {
            List<Document> placements = new ArrayList<>();
            for (Document placement : plan.getList("placements", Document.class, List.of())) {
                placements.add(new Document("code", placement.getString("pensumItemCode"))
                        .append("plannedLevel", placement.get("plannedLevel")));
            }
            mongo.getCollection("academicPlans").updateOne(new Document("_id", plan.get("_id")),
                    new Document("$set", new Document("placements", placements)));
        }
    }
}

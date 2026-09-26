package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Puts the snapshot's first floor of the Edificio Central in place of the one in the database,
 * although somebody worked on it.
 *
 * <p>{@link V007_TracedCampus} never touches a floor somebody worked on. The first floor of the
 * Edificio Central was edited in the floor editor over a draft drawn by eye; now that it is drawn
 * on its sanitary route plan, its editor asked for that drawing to take its place. What is replaced
 * is not lost: the floor as it was, with every one of its spaces, is kept in
 * {@code map_replaced_floors} first.
 *
 * <p>Runs once. The floor it writes is one a load wrote - version 0, spaces never updated - so
 * later snapshots redraw it like any other untouched floor.
 */
@ChangeUnit(id = "map-redraw-ec-p1-v008", order = "008", author = "kapp")
public class V008_RedrawnCentralGroundFloor {

    static final String BUILDING = "EC";
    static final String FLOOR = "P1";

    /** Where a floor is kept, as it was, before a redraw takes its place. */
    static final String REPLACED = "map_replaced_floors";

    private static final Logger log = LoggerFactory.getLogger(V008_RedrawnCentralGroundFloor.class);

    @Execution
    public void execute(MongoTemplate mongo, Environment environment) {
        if (!environment.getProperty(V007_TracedCampus.ENABLED, Boolean.class, true)) {
            log.info("{} {} not redrawn: {} is false", BUILDING, FLOOR, V007_TracedCampus.ENABLED);
            return;
        }
        int kept = redraw(mongo, SurveySnapshot.load(), BUILDING, FLOOR, Instant.now());
        log.info("{} {} redrawn from the snapshot; the floor it replaced and its {} space(s) are in {}",
                BUILDING, FLOOR, kept, REPLACED);
    }

    /**
     * Nothing to put back automatically: the floor it replaced is in {@code map_replaced_floors},
     * whole, for whoever wants it back.
     */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Nothing to undo.
    }

    /**
     * Keeps the stored floor aside, lets it count as untouched, and loads that floor of the
     * snapshot over it.
     *
     * @return how many spaces the floor had, now kept aside; 0 when the building or the floor is
     *         not there yet, in which case the traced campus load writes it anyway
     */
    static int redraw(MongoTemplate mongo, List<SurveySnapshot.Building> snapshot, String buildingCode,
                      String floorCode, Instant now) {
        SurveySnapshot.Building surveyed = snapshot.stream().filter(b -> b.code().equals(buildingCode))
                .findFirst().orElseThrow(() -> new IllegalStateException(buildingCode + " is not in the snapshot"));
        SurveySnapshot.SnapshotFloor floor = surveyed.floors().stream().filter(f -> f.code().equals(floorCode))
                .findFirst().orElseThrow(() -> new IllegalStateException(buildingCode + " " + floorCode
                        + " is not in the snapshot"));
        BuildingDocument stored = mongo.findOne(query(where("code").is(buildingCode)), BuildingDocument.class);
        if (stored == null || stored.floor(floorCode).isEmpty()) {
            return 0;
        }

        Document raw = mongo.getCollection("buildings").find(new Document("_id", stored.id())).first();
        Document oldFloor = raw.getList("floors", Document.class).stream()
                .filter(f -> floorCode.equals(f.getString("code"))).findFirst().orElseThrow();
        Document onFloor = new Document("buildingId", stored.id()).append("floorCode", floorCode);
        List<Document> oldSpaces = mongo.getCollection("spaces").find(onFloor).into(new ArrayList<>());
        mongo.getCollection(REPLACED).insertOne(new Document("building", buildingCode)
                .append("floor", oldFloor).append("spaces", oldSpaces).append("replacedAt", Date.from(now)));

        // As a load left it: no layout saved, every space written with the building.
        Date loaded = Date.from(stored.createdAt());
        mongo.getCollection("buildings").updateOne(
                new Document("_id", stored.id()).append("floors.code", floorCode),
                new Document("$set", new Document("floors.$.version", 0L)));
        mongo.getCollection("spaces").updateMany(onFloor,
                new Document("$set", new Document("createdAt", loaded).append("updatedAt", loaded)));

        SurveySnapshot.Building justThisFloor = new SurveySnapshot.Building(surveyed.code(), surveyed.name(),
                surveyed.campus(), surveyed.description(), surveyed.aliases(), surveyed.wings(), List.of(floor));
        V007_TracedCampus.apply(mongo, List.of(justThisFloor), now);
        return oldSpaces.size();
    }
}

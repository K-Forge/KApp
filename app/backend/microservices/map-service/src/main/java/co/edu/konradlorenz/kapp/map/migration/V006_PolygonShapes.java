package co.edu.konradlorenz.kapp.map.migration;

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
 * Moves every floor and space from the grid to polygons in the floor's own units.
 *
 * <p>A floor of {@code gridRows} by {@code gridColumns} cells becomes a drawing
 * {@link GridCells#CELL} units per cell wide and high. A placed space becomes the rectangle of
 * cells it covered, a corridor point the centre of its cell: everything stays where it was drawn,
 * and can now be reshaped to what the plan shows.
 *
 * <p>Written against raw documents, since the domain records no longer describe the grid. Nothing
 * else is touched - not {@code updatedAt}, not a floor's {@code version} - because a conversion is
 * not an edit: {@code V007_TracedCampus} tells the floors somebody worked on from those nobody did
 * by exactly those fields.
 *
 * <p>Re-running it is harmless: a floor that already has a {@code width}, and a space without
 * {@code rowSpan}, are skipped.
 */
@ChangeUnit(id = "map-polygon-shapes-v006", order = "006", author = "kapp")
public class V006_PolygonShapes {

    private static final Logger log = LoggerFactory.getLogger(V006_PolygonShapes.class);

    @Execution
    public void execute(MongoTemplate mongo) {
        Result result = convert(mongo);
        log.info("Polygon shapes: {} floor(s) and {} space(s) converted from the grid",
                result.floors(), result.spaces());
    }

    /**
     * Nothing to put back: the grid fields are gone from the code that would read them, and a
     * half-converted database is finished by running this again.
     */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Nothing to undo.
    }

    record Result(int floors, int spaces) {
    }

    static Result convert(MongoTemplate mongo) {
        int floorsConverted = 0;
        for (Document building : mongo.getCollection("buildings").find()) {
            @SuppressWarnings("unchecked")
            List<Document> floors = (List<Document>) building.getOrDefault("floors", List.of());
            if (floors.stream().allMatch(f -> f.containsKey("width"))) {
                continue;
            }
            List<Document> converted = new ArrayList<>();
            for (Document floor : floors) {
                if (floor.containsKey("width")) {
                    converted.add(floor);
                    continue;
                }
                Document next = new Document(floor);
                next.put("width", atLeast(next.remove("gridColumns"), 1) * GridCells.CELL);
                next.put("height", atLeast(next.remove("gridRows"), 1) * GridCells.CELL);
                next.put("outline", List.of());
                next.put("corridors", corridors(floor));
                converted.add(next);
                floorsConverted++;
            }
            mongo.getCollection("buildings").updateOne(new Document("_id", building.get("_id")),
                    new Document("$set", new Document("floors", converted)));
        }

        int spacesConverted = 0;
        for (Document space : mongo.getCollection("spaces").find(new Document("rowSpan", new Document("$exists", true)))) {
            Object row = space.get("gridRow");
            Object column = space.get("gridColumn");
            Document update = new Document("$unset", new Document("gridRow", "").append("gridColumn", "")
                    .append("rowSpan", "").append("colSpan", ""));
            if (row instanceof Number r && column instanceof Number c) {
                update.append("$set", new Document("shape", GridCells.rectangle(r.intValue(), c.intValue(),
                                atLeast(space.get("rowSpan"), 1), atLeast(space.get("colSpan"), 1)).stream()
                        .map(p -> new Document("x", p.x()).append("y", p.y())).toList()));
            }
            mongo.getCollection("spaces").updateOne(new Document("_id", space.get("_id")), update);
            spacesConverted++;
        }
        return new Result(floorsConverted, spacesConverted);
    }

    private static List<Document> corridors(Document floor) {
        @SuppressWarnings("unchecked")
        List<Document> corridors = (List<Document>) floor.getOrDefault("corridors", List.of());
        return corridors.stream().map(corridor -> {
            @SuppressWarnings("unchecked")
            List<Document> path = (List<Document>) corridor.getOrDefault("path", List.of());
            Document next = new Document(corridor);
            next.put("path", path.stream()
                    .map(p -> GridCells.centre(atLeast(p.get("row"), 0), atLeast(p.get("col"), 0)))
                    .map(p -> new Document("x", p.x()).append("y", p.y()))
                    .toList());
            return next;
        }).toList();
    }

    /** A stored number, never below {@code min}: a span of zero was always drawn as one cell. */
    private static int atLeast(Object value, int min) {
        return value instanceof Number n ? Math.max(n.intValue(), min) : min;
    }
}

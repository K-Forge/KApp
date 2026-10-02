package co.edu.konradlorenz.kapp.map.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the snapshot load last wrote, building by building: the base a later load compares the
 * database with, to tell what somebody changed in the portal from what the snapshot changed.
 *
 * <p>Without it a load could only see that a floor had been saved, not what was done to it, so a
 * floor somebody named one room on could never be redrawn again. Kept in
 * {@code map_snapshot_bases}, one document per building, as the snapshot file's JSON.
 */
final class SnapshotBases {

    static final String COLLECTION = "map_snapshot_bases";

    private static final ObjectMapper JSON = new ObjectMapper();

    private SnapshotBases() {
    }

    /** The building as the last load wrote it, or empty if no load has recorded it yet. */
    static Optional<SurveySnapshot.Building> find(MongoTemplate mongo, String code) {
        Document document = mongo.getCollection(COLLECTION).find(new Document("_id", code)).first();
        if (document == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(JSON.readValue(document.getString("json"), SurveySnapshot.Building.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The recorded base of " + code + " cannot be read", e);
        }
    }

    /** That building's floor as the last load wrote it, or empty. */
    static Optional<SurveySnapshot.SnapshotFloor> floor(Optional<SurveySnapshot.Building> base, String floorCode) {
        return base.flatMap(b -> b.floors().stream().filter(f -> f.code().equals(floorCode)).findFirst());
    }

    /**
     * Records what a load wrote: the building's own fields as the snapshot has them, and the floors
     * in {@code written} as the snapshot draws them. A floor the load left alone keeps the base it
     * had - it is still somebody's work on that one - or has none.
     */
    static void record(MongoTemplate mongo, SurveySnapshot.Building surveyed, Set<String> written) {
        Optional<SurveySnapshot.Building> before = find(mongo, surveyed.code());
        Map<String, SurveySnapshot.SnapshotFloor> floors = new LinkedHashMap<>();
        before.ifPresent(b -> b.floors().forEach(f -> floors.put(f.code(), f)));
        surveyed.floors().stream().filter(f -> written.contains(f.code())).forEach(f -> floors.put(f.code(), f));
        SurveySnapshot.Building base = new SurveySnapshot.Building(surveyed.code(), surveyed.name(), surveyed.campus(),
                surveyed.description(), surveyed.aliases(), surveyed.wings(), new ArrayList<>(floors.values()),
                surveyed.placement(), surveyed.footprint());
        try {
            mongo.getCollection(COLLECTION).replaceOne(new Document("_id", base.code()),
                    new Document("_id", base.code()).append("json", JSON.writeValueAsString(base))
                            .append("recordedAt", new Date()),
                    new ReplaceOptions().upsert(true));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The base of " + base.code() + " cannot be written", e);
        }
    }

    /** Every floor code of the building: what a load that wrote it whole records. */
    static Set<String> allFloors(SurveySnapshot.Building surveyed) {
        return Set.copyOf(surveyed.floors().stream().map(SurveySnapshot.SnapshotFloor::code).toList());
    }
}

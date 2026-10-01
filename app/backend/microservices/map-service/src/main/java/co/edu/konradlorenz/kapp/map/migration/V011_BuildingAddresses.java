package co.edu.konradlorenz.kapp.map.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Map;

/**
 * Gives each building of the Sede Principal its street address, once, and the names people call
 * two of them by that the seed did not have: "Casita blanca" for the Medio Universitario's house and
 * "Edificio Luna" for the Edificio Administrativo.
 *
 * <p>The addresses are the ones docs/map/LEVANTAMIENTO.md gathered while placing the buildings on
 * the cadastre. An address somebody has already written is theirs and is never replaced; a name a
 * building already has is not added twice.
 */
@ChangeUnit(id = "map-building-addresses-v011", order = "011", author = "kapp")
public class V011_BuildingAddresses {

    private static final Logger log = LoggerFactory.getLogger(V011_BuildingAddresses.class);

    static final Map<String, String> ADDRESSES = Map.of(
            "EC", "Cra. 9 Bis # 62-43",
            "TK", "Cra. 9A # 62-02",
            "EA", "Cra. 9A # 62-27",
            "BI", "Cl. 62 # 9-81",
            "MU", "Cl. 62 # 9-65",
            "CPC1", "Cra. 9 # 61-38",
            "CPC2", "Cra. 8 # 64-42, piso 4",
            "JAAB", "Cra. 10 # 64-65");

    static final Map<String, String> NICKNAMES = Map.of(
            "MU", "Casita blanca",
            "EA", "Edificio Luna");

    @Execution
    public void execute(MongoTemplate mongo, Environment environment) {
        if (!environment.getProperty(V007_TracedCampus.ENABLED, Boolean.class, true)) {
            log.info("Building addresses not set: {} is false", V007_TracedCampus.ENABLED);
            return;
        }
        log.info("Building addresses: {} set, {} names added", addresses(mongo), nicknames(mongo));
    }

    /** Nothing to undo: an address or a name somebody has since corrected is theirs. */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Nothing to undo.
    }

    public static long addresses(MongoTemplate mongo) {
        long set = 0;
        for (var entry : ADDRESSES.entrySet()) {
            Query unaddressed = Query.query(Criteria.where("code").is(entry.getKey()).and("address").is(null));
            set += mongo.updateFirst(unaddressed, Update.update("address", entry.getValue()), "buildings").getModifiedCount();
        }
        return set;
    }

    public static long nicknames(MongoTemplate mongo) {
        long added = 0;
        for (var entry : NICKNAMES.entrySet()) {
            Query building = Query.query(Criteria.where("code").is(entry.getKey()));
            added += mongo.updateFirst(building, new Update().addToSet("aliases", entry.getValue()), "buildings").getModifiedCount();
        }
        return added;
    }
}

package co.edu.konradlorenz.kapp.auth.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Drops {@code invitation_codes}, which {@code V002} made (#255). Members sign in with Microsoft,
 * a visitor gets a day pass, and password registration, for local development only, needs no code.
 *
 * <p>Order 007, not 006: auth-service's 006 is the deletion of the fake sign-in's accounts (#246),
 * and the two are independent. Change units are append-only: never edit one that has run; add a
 * new one.
 */
@ChangeUnit(id = "auth-drop-invitation-codes-v007", order = "007", author = "kapp")
public class V007_DropInvitationCodes {

    private static final Logger log = LoggerFactory.getLogger(V007_DropInvitationCodes.class);

    static final String COLLECTION = "invitation_codes";

    @Execution
    public void execute(MongoTemplate mongo) {
        if (mongo.collectionExists(COLLECTION)) {
            mongo.dropCollection(COLLECTION);
            log.info("{} dropped", COLLECTION);
        }
    }

    /** Nothing to put back: no code is used any more. */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Deliberately empty.
    }
}

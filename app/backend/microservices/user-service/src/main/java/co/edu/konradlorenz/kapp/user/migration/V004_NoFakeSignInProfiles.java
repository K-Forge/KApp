package co.edu.konradlorenz.kapp.user.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deletes the profiles of the accounts the fake sign-in made (#246): auth-service's
 * {@code V006_NoFakeSignInAccounts} deletes the accounts.
 *
 * <p>Only an address ending in {@code @kforge.dev} exactly, the domain the fake OIDC server and
 * {@code scripts/create-dev-accounts.sh} invented, which nobody signs in to with Microsoft. If
 * auth-service ever kept such an account because it carries a real Microsoft id, its profile is
 * made again at its next sign-in, as every sign-in brings its profile up to date.
 *
 * <p>Change units are append-only: never edit one that has run; add a new one.
 */
@ChangeUnit(id = "user-no-fake-sign-in-profiles-v004", order = "004", author = "kapp")
public class V004_NoFakeSignInProfiles {

    private static final Logger log = LoggerFactory.getLogger(V004_NoFakeSignInProfiles.class);

    static final String COLLECTION = "users";
    static final Pattern FAKE_ADDRESS = Pattern.compile("^[^@\\s]+@kforge\\.dev$", Pattern.CASE_INSENSITIVE);

    @Execution
    public void execute(MongoTemplate mongo) {
        List<String> deleted = new ArrayList<>();
        for (Document profile : mongo.getCollection(COLLECTION)
                .find(new Document("email", new Document("$regex", FAKE_ADDRESS.pattern()).append("$options", "i")))) {
            String email = profile.getString("email");
            if (email == null || !FAKE_ADDRESS.matcher(email).matches()) {
                continue;
            }
            mongo.getCollection(COLLECTION).deleteOne(new Document("_id", profile.get("_id")));
            deleted.add(email);
        }
        log.info("{} profile(s) of the fake sign-in deleted: {}", deleted.size(), deleted);
    }

    /** Nothing to put back: the people were invented. */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Deliberately empty.
    }
}

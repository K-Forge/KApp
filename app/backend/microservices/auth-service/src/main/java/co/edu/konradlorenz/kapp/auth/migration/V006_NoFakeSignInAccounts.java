package co.edu.konradlorenz.kapp.auth.migration;

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
 * Deletes the accounts the fake sign-in made, with their sessions (#246).
 *
 * <p>The fake OIDC server and {@code scripts/create-dev-accounts.sh} invented people under
 * {@code @kforge.dev}, a domain nobody signs in to with Microsoft. When laptops ran the stack with
 * them against the team's Atlas cluster, those accounts landed there, some of them administrators.
 * Now the fake server runs only with the {@code local-db} profile's own database, and nothing
 * makes them on Atlas again.
 *
 * <p>Two guards keep a real account out of it. The address must end in {@code @kforge.dev}
 * exactly. And an account signed in with Microsoft must carry an id the fake server gives: one of
 * its fixed ids, or a name that is no id at all. An {@code @kforge.dev} address with a real
 * Microsoft id would be a tenant's own person, and is kept, with a warning.
 *
 * <p>Its user-service half is that service's {@code V004_NoFakeSignInProfiles}. Change units are
 * append-only: never edit one that has run; add a new one.
 */
@ChangeUnit(id = "auth-no-fake-sign-in-accounts-v006", order = "006", author = "kapp")
public class V006_NoFakeSignInAccounts {

    private static final Logger log = LoggerFactory.getLogger(V006_NoFakeSignInAccounts.class);

    static final Pattern FAKE_ADDRESS = Pattern.compile("^[^@\\s]+@kforge\\.dev$", Pattern.CASE_INSENSITIVE);
    /** The fixed {@code oid}s in fake-oidc/config.json. */
    static final String FAKE_OID_PREFIX = "6f2a3c1e-7b4d-4e5a-9c6f-";
    private static final Pattern GUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);

    @Execution
    public void execute(MongoTemplate mongo) {
        List<String> deleted = new ArrayList<>();
        for (Document account : mongo.getCollection("credentials")
                .find(new Document("email", new Document("$regex", FAKE_ADDRESS.pattern()).append("$options", "i")))) {
            String email = account.getString("email");
            if (email == null || !FAKE_ADDRESS.matcher(email).matches()) {
                continue;
            }
            if (!madeByTheFakeSignIn(account)) {
                log.warn("{} has a Microsoft id the fake sign-in does not give: kept", email);
                continue;
            }
            String userId = account.getString("userId");
            mongo.getCollection("credentials").deleteOne(new Document("_id", account.get("_id")));
            if (userId != null) {
                mongo.getCollection("refresh_tokens").deleteMany(new Document("userId", userId));
            }
            deleted.add(email);
        }
        log.info("{} account(s) of the fake sign-in deleted: {}", deleted.size(), deleted);
    }

    static boolean madeByTheFakeSignIn(Document account) {
        if (!"ENTRA_ID".equals(account.getString("provider"))) {
            return true;
        }
        String oid = account.getString("externalId");
        return oid == null || oid.startsWith(FAKE_OID_PREFIX) || !GUID.matcher(oid).matches();
    }

    /** Nothing to put back: the accounts were invented, and the fake sign-in makes them again where it runs. */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        // Deliberately empty.
    }
}

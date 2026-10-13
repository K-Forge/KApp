package co.edu.konradlorenz.kapp.auth.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * The person's id at Microsoft, {@code oid}, which a sign-in with Microsoft finds its account by.
 * Unique, so two accounts never claim one person; sparse, because a local account has none.
 *
 * <p>Change units are append-only. Never edit one that has run; add a new one.
 */
@ChangeUnit(id = "auth-credential-external-id-v005", order = "005", author = "kapp")
public class V005_CredentialExternalId {

    static final String INDEX = "uk_credentials_external_id";

    @Execution
    public void execute(MongoTemplate mongo) {
        mongo.indexOps("credentials").createIndex(
                new Index().on("externalId", Sort.Direction.ASC).unique().sparse().named(INDEX));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        mongo.indexOps("credentials").dropIndex(INDEX);
    }
}

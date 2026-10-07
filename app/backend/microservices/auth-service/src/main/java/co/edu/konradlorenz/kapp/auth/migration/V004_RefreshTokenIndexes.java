package co.edu.konradlorenz.kapp.auth.migration;

import co.edu.konradlorenz.kapp.auth.domain.RefreshToken;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import java.util.concurrent.TimeUnit;

/**
 * Indexes for refresh tokens, including the one that removes a token once it has expired.
 *
 * <p>Change units are append-only. Never edit one that has run; add a new one.
 */
@ChangeUnit(id = "auth-refresh-tokens-v004", order = "004", author = "kapp")
public class V004_RefreshTokenIndexes {

    private static final String COLLECTION = RefreshToken.COLLECTION;

    @Execution
    public void execute(MongoTemplate mongo) {
        // Every renewal and every sign-out looks a token up by its hash, and two tokens with one
        // hash would make a session ambiguous.
        mongo.indexOps(COLLECTION).createIndex(
                new Index().on("tokenHash", Sort.Direction.ASC).unique().named("uk_refresh_token_hash"));

        // Reuse and sign-out revoke a whole family; a deactivation, every family of an account.
        mongo.indexOps(COLLECTION).createIndex(
                new Index().on("familyId", Sort.Direction.ASC).named("ix_refresh_token_family"));
        mongo.indexOps(COLLECTION).createIndex(
                new Index().on("userId", Sort.Direction.ASC).named("ix_refresh_token_user"));

        // A token past expiresAt renews nothing, used or not, so the database removes it, as it
        // removes a visitor pass past its retention. A used token stays until then on purpose:
        // that is how a copy presented later is recognised as reuse.
        mongo.getCollection(COLLECTION).createIndex(
                Indexes.ascending("expiresAt"),
                new IndexOptions().name("ttl_refresh_token_expiry").expireAfter(0L, TimeUnit.SECONDS));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        mongo.indexOps(COLLECTION).dropIndex("uk_refresh_token_hash");
        mongo.indexOps(COLLECTION).dropIndex("ix_refresh_token_family");
        mongo.indexOps(COLLECTION).dropIndex("ix_refresh_token_user");
        mongo.indexOps(COLLECTION).dropIndex("ttl_refresh_token_expiry");
    }
}

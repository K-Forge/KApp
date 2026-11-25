package co.edu.konradlorenz.kapp.auth;

import co.edu.konradlorenz.kapp.auth.migration.V006_NoFakeSignInAccounts;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link V006_NoFakeSignInAccounts}. Mongock ran it when the context started, on an empty
 * collection; these tests write accounts by hand and run it again.
 */
class NoFakeSignInAccountsMigrationTest extends AbstractAuthIntegrationTest {

    private final V006_NoFakeSignInAccounts v006 = new V006_NoFakeSignInAccounts();

    @BeforeEach
    void empty() {
        mongoTemplate.getCollection("credentials").deleteMany(new Document());
        mongoTemplate.getCollection("refresh_tokens").deleteMany(new Document());
    }

    @Test
    @DisplayName("the fake sign-in's accounts go, with their sessions, whether by Microsoft or by password")
    void fakeAccounts_areDeleted() {
        account("u1", "estudiante@kforge.dev", "ENTRA_ID", "6f2a3c1e-7b4d-4e5a-9c6f-000000000001");
        account("u2", "Visitante@KFORGE.dev", "ENTRA_ID", "visitante");
        account("u3", "brian@kforge.dev", "LOCAL", null);
        session("u1");
        session("u3");

        v006.execute(mongoTemplate);

        assertThat(mongoTemplate.getCollection("credentials").countDocuments()).isZero();
        assertThat(mongoTemplate.getCollection("refresh_tokens").countDocuments()).isZero();
    }

    @Test
    @DisplayName("a tenant's account, or a @kforge.dev one with a real Microsoft id, stays")
    void realAccounts_stay() {
        account("t1", "ana.ruiz@konradlorenz.edu.co", "ENTRA_ID", "0f1e2d3c-4b5a-4968-8776-655443322110");
        account("t2", "someone@kforge.dev.example.org", "LOCAL", null);
        account("t3", "kept@kforge.dev", "ENTRA_ID", "0f1e2d3c-4b5a-4968-8776-655443322111");
        session("t1");

        v006.execute(mongoTemplate);

        assertThat(mongoTemplate.getCollection("credentials").countDocuments()).isEqualTo(3);
        assertThat(mongoTemplate.getCollection("refresh_tokens").countDocuments()).isEqualTo(1);
    }

    private void account(String userId, String email, String provider, String externalId) {
        mongoTemplate.getCollection("credentials").insertOne(new Document("userId", userId)
                .append("email", email).append("provider", provider).append("externalId", externalId)
                .append("roles", java.util.List.of("ROLE_STAFF")).append("status", "ACTIVE"));
    }

    private void session(String userId) {
        mongoTemplate.getCollection("refresh_tokens").insertOne(new Document("userId", userId)
                .append("tokenHash", "hash-" + userId).append("familyId", "family-" + userId));
    }
}

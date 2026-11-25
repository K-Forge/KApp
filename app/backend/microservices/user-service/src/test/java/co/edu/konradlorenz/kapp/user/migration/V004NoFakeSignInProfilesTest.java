package co.edu.konradlorenz.kapp.user.migration;

import co.edu.konradlorenz.kapp.user.AbstractUserServiceTest;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link V004_NoFakeSignInProfiles}. Mongock ran it when the context started, on an empty
 * collection; these tests write profiles by hand and run it again.
 */
class V004NoFakeSignInProfilesTest extends AbstractUserServiceTest {

    private final V004_NoFakeSignInProfiles v004 = new V004_NoFakeSignInProfiles();

    @BeforeEach
    void empty() {
        mongoTemplate.getCollection(V004_NoFakeSignInProfiles.COLLECTION).deleteMany(new Document());
    }

    @Test
    @DisplayName("the fake sign-in's profiles go, and every other one stays")
    void onlyFakeProfiles_areDeleted() {
        profile("estudiante@kforge.dev");
        profile("Administrativo@KForge.dev");
        profile("pepito.perez@konradlorenz.edu.co");
        profile("someone@kforge.dev.example.org");
        profile("kforge.dev@konradlorenz.edu.co");

        v004.execute(mongoTemplate);

        assertThat(mongoTemplate.getCollection(V004_NoFakeSignInProfiles.COLLECTION).find()
                .map(found -> found.getString("email")).into(new java.util.ArrayList<>()))
                .containsExactlyInAnyOrder("pepito.perez@konradlorenz.edu.co", "someone@kforge.dev.example.org",
                        "kforge.dev@konradlorenz.edu.co");
    }

    private void profile(String email) {
        mongoTemplate.getCollection(V004_NoFakeSignInProfiles.COLLECTION).insertOne(new Document("email", email)
                .append("firstName", "A").append("lastName", "B").append("roles", List.of("ROLE_STAFF"))
                .append("active", true));
    }
}

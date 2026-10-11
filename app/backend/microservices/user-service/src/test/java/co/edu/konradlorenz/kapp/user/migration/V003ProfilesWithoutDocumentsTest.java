package co.edu.konradlorenz.kapp.user.migration;

import co.edu.konradlorenz.kapp.user.AbstractUserServiceTest;
import co.edu.konradlorenz.kapp.user.domain.UserProfile;
import co.edu.konradlorenz.kapp.user.domain.UserRole;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link V003_ProfilesWithoutDocuments} on documents shaped as user 0.x stored them: one
 * {@code role}, an identity document, a phone number and the academic block copied at registration.
 *
 * <p>Mongock already ran the change unit when the context started, on an empty collection. These
 * tests write the old shape by hand and run it again, which is also how it proves that running it
 * twice is harmless.
 */
class V003ProfilesWithoutDocumentsTest extends AbstractUserServiceTest {

    private static final String STUDENT_ID = "d0000000-0000-0000-0000-000000000001";
    private static final String GUEST_ID = "d0000000-0000-0000-0000-000000000002";
    private static final String CURRENT_ID = "d0000000-0000-0000-0000-000000000003";

    private final V003_ProfilesWithoutDocuments v003 = new V003_ProfilesWithoutDocuments();

    /** Leaves the collection's indexes as Mongock left them, whatever a test did to them. */
    @AfterEach
    void restoreIndexes() {
        if (indexNames().contains(V003_ProfilesWithoutDocuments.STUDENT_CODE_INDEX)) {
            mongoTemplate.indexOps(V003_ProfilesWithoutDocuments.COLLECTION)
                    .dropIndex(V003_ProfilesWithoutDocuments.STUDENT_CODE_INDEX);
        }
        v003.execute(mongoTemplate);
    }

    @Test
    @DisplayName("a 0.x student loses the document, the phone and the academic copy, and holds its role in a list")
    void oldStudent_isBroughtToOnePointZero() {
        insertOldShape(STUDENT_ID, "pepito.perez@konradlorenz.edu.co", "ROLE_STUDENT", true);

        v003.execute(mongoTemplate);

        Document stored = raw(STUDENT_ID);
        assertThat(stored.getList("roles", String.class)).containsExactly("ROLE_STUDENT");
        assertThat(stored).doesNotContainKeys("role", "identification", "phone", "academic");
        assertThat(stored)
                .containsEntry("email", "pepito.perez@konradlorenz.edu.co")
                .containsEntry("firstName", "Pepito")
                .containsEntry("avatarUrl", "https://cdn.kapp.konradlorenz.edu.co/avatars/3f8a1c2e.jpg")
                .containsEntry("active", true);

        UserProfile read = reload(STUDENT_ID);
        assertThat(read.roles()).containsExactly(UserRole.ROLE_STUDENT);
        assertThat(read.createdAt()).isEqualTo(Instant.parse("2026-02-02T13:00:00Z"));
    }

    /** {@code ROLE_GUEST} is kept in the enum so that such a profile still reads. */
    @Test
    @DisplayName("a 0.x guest keeps its role, now in a list, and still reads")
    void oldGuest_stillReads() {
        insertOldShape(GUEST_ID, "maria.rodriguez@gmail.com", "ROLE_GUEST", false);

        v003.execute(mongoTemplate);

        assertThat(reload(GUEST_ID).roles()).containsExactly(UserRole.ROLE_GUEST);
    }

    @Test
    @DisplayName("a profile already at 1.0 is not touched")
    void currentProfile_isUntouched() {
        save(admin(CURRENT_ID, "ana.ruiz@konradlorenz.edu.co", "Ana", "Ruiz Mejía"));
        Document before = raw(CURRENT_ID);

        v003.execute(mongoTemplate);

        assertThat(raw(CURRENT_ID)).isEqualTo(before);
    }

    @Test
    @DisplayName("a document that somehow holds both keeps its list of roles and loses the single role")
    void bothShapes_keepTheList() {
        insertOldShape(STUDENT_ID, "pepito.perez@konradlorenz.edu.co", "ROLE_STUDENT", true);
        mongoTemplate.getCollection(V003_ProfilesWithoutDocuments.COLLECTION).updateOne(
                new Document("_id", STUDENT_ID),
                new Document("$set", new Document("roles", List.of("ROLE_STAFF", "ROLE_ADMIN"))));

        v003.execute(mongoTemplate);

        Document stored = raw(STUDENT_ID);
        assertThat(stored.getList("roles", String.class)).containsExactly("ROLE_STAFF", "ROLE_ADMIN");
        assertThat(stored).doesNotContainKey("role");
    }

    @Test
    @DisplayName("the student-code index goes with the field, and roles gets one for the directory's filter")
    void indexes_followTheFields() {
        mongoTemplate.indexOps(V003_ProfilesWithoutDocuments.COLLECTION).createIndex(
                new Index().on("academic.studentCode", Sort.Direction.ASC).sparse()
                        .named(V003_ProfilesWithoutDocuments.STUDENT_CODE_INDEX));

        v003.execute(mongoTemplate);

        assertThat(indexNames())
                .contains(V003_ProfilesWithoutDocuments.ROLES_INDEX)
                .doesNotContain(V003_ProfilesWithoutDocuments.STUDENT_CODE_INDEX);
    }

    @Test
    @DisplayName("running it a second time changes nothing")
    void secondRun_changesNothing() {
        insertOldShape(STUDENT_ID, "pepito.perez@konradlorenz.edu.co", "ROLE_STUDENT", true);
        v003.execute(mongoTemplate);
        Document once = raw(STUDENT_ID);

        v003.execute(mongoTemplate);

        assertThat(raw(STUDENT_ID)).isEqualTo(once);
    }

    /** What was removed cannot come back: KApp no longer keeps it anywhere. */
    @Test
    @DisplayName("the rollback gives each profile back a single role, its first, and drops the roles index")
    void rollback_restoresOneRole() {
        save(admin(CURRENT_ID, "ana.ruiz@konradlorenz.edu.co", "Ana", "Ruiz Mejía"));

        v003.rollback(mongoTemplate);

        Document stored = raw(CURRENT_ID);
        assertThat(stored).containsEntry("role", "ROLE_STAFF").doesNotContainKey("roles");
        assertThat(indexNames()).doesNotContain(V003_ProfilesWithoutDocuments.ROLES_INDEX);
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    /** A profile exactly as user 0.x wrote it. The values are invented. */
    private void insertOldShape(String id, String email, String role, boolean withAcademic) {
        Date created = Date.from(Instant.parse("2026-02-02T13:00:00Z"));
        Document profile = new Document("_id", id)
                .append("email", email)
                .append("firstName", "Pepito")
                .append("lastName", "Perez Gomez")
                .append("identification", new Document("type", "CC").append("number", "1032456789"))
                .append("phone", "+573105551234")
                .append("avatarUrl", "https://cdn.kapp.konradlorenz.edu.co/avatars/3f8a1c2e.jpg")
                .append("role", role)
                .append("active", true)
                .append("searchTokens", List.of("pepito", "perez", "gomez"))
                .append("createdAt", created)
                .append("updatedAt", created);
        if (withAcademic) {
            profile.append("academic", new Document("studentCode", "506900001")
                    .append("programCode", "506").append("pensumCode", "1015").append("currentLevel", 5));
        }
        mongoTemplate.getCollection(V003_ProfilesWithoutDocuments.COLLECTION).insertOne(profile);
    }

    private Document raw(String id) {
        return mongoTemplate.getCollection(V003_ProfilesWithoutDocuments.COLLECTION)
                .find(new Document("_id", id)).first();
    }

    private List<String> indexNames() {
        return mongoTemplate.indexOps(V003_ProfilesWithoutDocuments.COLLECTION).getIndexInfo().stream()
                .map(IndexInfo::getName)
                .toList();
    }
}

package co.edu.konradlorenz.kapp.user.web;

import co.edu.konradlorenz.kapp.user.AbstractUserServiceTest;
import co.edu.konradlorenz.kapp.user.domain.StoredInstant;
import co.edu.konradlorenz.kapp.user.domain.UserProfile;
import co.edu.konradlorenz.kapp.user.domain.UserRole;
import co.edu.konradlorenz.kapp.user.client.CredentialStatusUpdate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The directory and activation endpoints under {@code /api/users}, exercised as an admin.
 *
 * <p>{@link UserAuthorizationMatrixTest} covers who may call these; this class covers
 * what they do: filtering, pagination and its cap, the accent-insensitive search through
 * the actual HTTP surface (the index itself is proven separately by
 * {@code DirectorySearchIndexTest}), and the activation flow.
 */
class UserAdminControllerTest extends AbstractUserServiceTest {

    private static final String ADMIN_ID = "c0000000-0000-0000-0000-000000000001";
    private static final String TARGET_ID = "c0000000-0000-0000-0000-000000000002";

    // ---------------------------------------------------------------------------------
    // GET /api/users - filters
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("no filters returns every account")
    void list_noFilters_returnsEveryone() throws Exception {
        save(student("s1", "s1@konradlorenz.edu.co", "One", "Student"));
        save(professor("p1", "p1@konradlorenz.edu.co", "One", "Professor"));

        mockMvc.perform(get("/api/users").with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("the role filter narrows the directory to that role only")
    void list_roleFilter_narrowsToThatRole() throws Exception {
        save(student("s1", "s1@konradlorenz.edu.co", "One", "Student"));
        save(professor("p1", "p1@konradlorenz.edu.co", "One", "Professor"));

        mockMvc.perform(get("/api/users").param("role", "ROLE_PROFESSOR")
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].roles", contains("ROLE_PROFESSOR")));
    }

    @Test
    @DisplayName("the role filter finds a permission held alongside a profile role, and that profile role too")
    void list_roleFilter_matchesAnyRoleHeld() throws Exception {
        save(student("s1", "s1@konradlorenz.edu.co", "One", "Student"));
        save(admin("a1", "ana.ruiz@konradlorenz.edu.co", "Ana", "Ruiz Mejía"));

        mockMvc.perform(get("/api/users").param("role", "ROLE_ADMIN")
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_STAFF, UserRole.ROLE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].roles", contains("ROLE_STAFF", "ROLE_ADMIN")));

        mockMvc.perform(get("/api/users").param("role", "ROLE_STAFF")
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_STAFF, UserRole.ROLE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value("a1"));
    }

    @Test
    @DisplayName("a role KApp does not know is rejected with 400")
    void list_unknownRole_isRejected() throws Exception {
        mockMvc.perform(get("/api/users").param("role", "ROLE_DEAN")
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("role"));
    }

    @Test
    @DisplayName("the active filter narrows the directory to that activation state")
    void list_activeFilter_narrowsToThatState() throws Exception {
        UserProfile stillActive = save(student("s1", "s1@konradlorenz.edu.co", "One", "Student"));
        UserProfile toDeactivate = save(student("s2", "s2@konradlorenz.edu.co", "Two", "Student"));
        save(toDeactivate.withActive(false, StoredInstant.now()));

        mockMvc.perform(get("/api/users").param("active", "false")
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value("s2"));

        assertThat(stillActive.active()).isTrue();
    }

    @Test
    @DisplayName("an accent-insensitive search finds Munoz from munoz through the HTTP layer")
    void list_search_isAccentInsensitive() throws Exception {
        save(student("s1", "laura.munoz@konradlorenz.edu.co", "Laura", "Muñoz Peña"));
        save(student("s2", "other@konradlorenz.edu.co", "Other", "Person"));

        mockMvc.perform(get("/api/users").param("q", "munoz")
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Muñoz Peña"));
    }

    // ---------------------------------------------------------------------------------
    // GET /api/users - pagination
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("the first page reports its own metadata correctly")
    void list_firstPage_reportsMetadata() throws Exception {
        for (int i = 0; i < 25; i++) {
            save(student("page-" + i, "page" + i + "@konradlorenz.edu.co", "P" + i, "Student"));
        }

        mockMvc.perform(get("/api/users").param("page", "0").param("size", "20")
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    @DisplayName("the last page holds only the remainder and reports itself as last")
    void list_lastPage_holdsRemainderAndReportsLast() throws Exception {
        for (int i = 0; i < 25; i++) {
            save(student("page-" + i, "page" + i + "@konradlorenz.edu.co", "P" + i, "Student"));
        }

        mockMvc.perform(get("/api/users").param("page", "1").param("size", "20")
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("a page size above the cap is rejected with 400, never clamped")
    void list_sizeAboveCap_isRejected() throws Exception {
        mockMvc.perform(get("/api/users").param("size", "101")
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("size"));
    }

    @Test
    @DisplayName("a page size below 1 is rejected with 400")
    void list_sizeBelowMinimum_isRejected() throws Exception {
        mockMvc.perform(get("/api/users").param("size", "0")
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("size"));
    }

    @Test
    @DisplayName("a negative page index is rejected with 400")
    void list_negativePage_isRejected() throws Exception {
        mockMvc.perform(get("/api/users").param("page", "-1")
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("page"));
    }

    @Test
    @DisplayName("an empty search string is rejected with 400")
    void list_emptyQuery_isRejected() throws Exception {
        mockMvc.perform(get("/api/users").param("q", "")
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("q"));
    }

    @Test
    @DisplayName("a search string over 100 characters is rejected with 400")
    void list_queryTooLong_isRejected() throws Exception {
        mockMvc.perform(get("/api/users").param("q", "a".repeat(101))
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("q"));
    }

    // ---------------------------------------------------------------------------------
    // GET /api/users/{userId}
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("an unknown id is a 404, not a 400")
    void getById_unknownId_is404() throws Exception {
        mockMvc.perform(get("/api/users/{userId}", "does-not-exist")
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isNotFound());
    }

    /** An administrator manages accounts, not records: a student's program is not theirs to see. */
    @Test
    @DisplayName("a known id returns that directory entry, with nothing academic")
    void getById_knownId_returnsEntryWithoutAcademic() throws Exception {
        save(fullyPopulated(TARGET_ID, "pepito.perez@konradlorenz.edu.co"));

        mockMvc.perform(get("/api/users/{userId}", TARGET_ID)
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TARGET_ID))
                .andExpect(jsonPath("$.email").value("pepito.perez@konradlorenz.edu.co"))
                .andExpect(jsonPath("$.roles", contains("ROLE_STUDENT")))
                .andExpect(jsonPath("$.academic").doesNotExist());

        verify(sinu, never()).student(any(), any());
    }

    /** {@code ROLE_GUEST} stays in the enum for exactly this: V003 turned such a profile's role into a list. */
    @Test
    @DisplayName("a profile written before user 1.0 with ROLE_GUEST still reads")
    void getById_legacyGuestProfile_stillReads() throws Exception {
        save(profile(TARGET_ID, "maria.rodriguez@gmail.com", "Maria", "Rodriguez", UserRole.ROLE_GUEST));

        mockMvc.perform(get("/api/users/{userId}", TARGET_ID)
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ROLE_GUEST")));
    }

    // ---------------------------------------------------------------------------------
    // PATCH /api/users/{userId}/status
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("deactivating an account flips active to false")
    void setStatus_deactivate_flipsActiveFlag() throws Exception {
        save(student(TARGET_ID, "target@konradlorenz.edu.co", "Target", "Account"));

        mockMvc.perform(patch("/api/users/{userId}/status", TARGET_ID)
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(reload(TARGET_ID).active()).isFalse();
    }

    @Test
    @DisplayName("setting the status an account already holds is idempotent")
    void setStatus_alreadyInThatState_isIdempotent() throws Exception {
        save(student(TARGET_ID, "target@konradlorenz.edu.co", "Target", "Account"));

        mockMvc.perform(patch("/api/users/{userId}/status", TARGET_ID)
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("changing the status of an unknown id is a 404")
    void setStatus_unknownId_is404() throws Exception {
        mockMvc.perform(patch("/api/users/{userId}/status", "does-not-exist")
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}"""))
                .andExpect(status().isNotFound());
    }

    // Deactivating is two facts in two databases. Before this, only the profile half happened:
    // the account stopped being listed as active and its owner kept signing in, because sign-in
    // is decided by the credential in auth-service and nothing ever suspended it.
    @Test
    @DisplayName("deactivating an account also suspends its credential in auth-service")
    void setStatus_deactivate_suspendsTheCredential() throws Exception {
        save(student(TARGET_ID, "target@konradlorenz.edu.co", "Target", "Account"));

        mockMvc.perform(patch("/api/users/{userId}/status", TARGET_ID)
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}"""))
                .andExpect(status().isOk());

        verify(credentialStatus).setStatus(TARGET_ID, new CredentialStatusUpdate(false));
    }

    @Test
    @DisplayName("reactivating an account lets it sign in again")
    void setStatus_reactivate_restoresTheCredential() throws Exception {
        save(student(TARGET_ID, "target@konradlorenz.edu.co", "Target", "Account"));

        mockMvc.perform(patch("/api/users/{userId}/status", TARGET_ID)
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": true}"""))
                .andExpect(status().isOk());

        verify(credentialStatus).setStatus(TARGET_ID, new CredentialStatusUpdate(true));
    }

    // The credential goes first on purpose: if the profile write then fails the account is
    // already locked out, and a retry finishes the job. The other order would leave a profile
    // marked inactive whose owner could still sign in - the state this whole change removed.
    @Test
    @DisplayName("when auth-service cannot be reached the profile is not flipped either")
    void setStatus_authServiceDown_leavesTheProfileAlone() throws Exception {
        save(student(TARGET_ID, "target@konradlorenz.edu.co", "Target", "Account"));
        doThrow(new IllegalStateException("auth-service is down"))
                .when(credentialStatus).setStatus(eq(TARGET_ID), any());

        mockMvc.perform(patch("/api/users/{userId}/status", TARGET_ID)
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}"""))
                .andExpect(status().is5xxServerError());

        assertThat(reload(TARGET_ID).active()).isTrue();
    }

    @Test
    @DisplayName("a null active flag is rejected with 400 rather than defaulting")
    void setStatus_nullActive_isRejected() throws Exception {
        save(student(TARGET_ID, "target@konradlorenz.edu.co", "Target", "Account"));

        mockMvc.perform(patch("/api/users/{userId}/status", TARGET_ID)
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": null}"""))
                .andExpect(status().isBadRequest());
    }
}

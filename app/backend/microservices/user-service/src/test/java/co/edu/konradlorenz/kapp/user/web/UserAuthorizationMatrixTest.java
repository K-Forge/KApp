package co.edu.konradlorenz.kapp.user.web;

import co.edu.konradlorenz.kapp.user.AbstractUserServiceTest;
import co.edu.konradlorenz.kapp.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every role, authenticated or not, against every endpoint this service exposes.
 *
 * <p>One test method per {@code (endpoint, caller)} pair and one assertion per test, so a
 * failure names the exact combination that broke rather than hiding it inside a loop. The
 * five endpoints are {@code GET/PATCH /api/users/me} (self-service, open to every profile
 * role) and {@code GET /api/users}, {@code GET /api/users/{userId}},
 * {@code PATCH /api/users/{userId}/status} (administration, {@code ROLE_ADMIN} only).
 *
 * <p>An administrator's token holds a profile role and the permission, as auth-service signs
 * it: {@code ROLE_STAFF} and {@code ROLE_ADMIN}. Another permission next to the profile role
 * opens nothing here.
 *
 * <p>Business behaviour - patch semantics, pagination, search, idempotency - is covered
 * elsewhere. This class only answers "who may call this at all".
 */
class UserAuthorizationMatrixTest extends AbstractUserServiceTest {

    private static final String GUEST_ID = "a0000000-0000-0000-0000-000000000001";
    private static final String STUDENT_ID = "a0000000-0000-0000-0000-000000000002";
    private static final String OTHER_STUDENT_ID = "a0000000-0000-0000-0000-000000000003";
    private static final String PROFESSOR_ID = "a0000000-0000-0000-0000-000000000004";
    private static final String ADMIN_ID = "a0000000-0000-0000-0000-000000000005";
    private static final String STAFF_ID = "a0000000-0000-0000-0000-000000000006";

    /** A trivial, always-valid patch body: the one field a person may edit. */
    private static final String TRIVIAL_PATCH = """
            {"avatarUrl": null}""";

    /** A visitor holds a day pass and has no profile, so none is seeded for GUEST_ID. */
    @BeforeEach
    void seedOneAccountPerRole() {
        save(student(STUDENT_ID, "student@konradlorenz.edu.co", "Student", "Account"));
        save(student(OTHER_STUDENT_ID, "other@konradlorenz.edu.co", "Other", "Student"));
        save(professor(PROFESSOR_ID, "professor@konradlorenz.edu.co", "Professor", "Account"));
        save(admin(ADMIN_ID, "admin@konradlorenz.edu.co", "Admin", "Account"));
        save(profile(STAFF_ID, "staff@konradlorenz.edu.co", "Staff", "Account", UserRole.ROLE_STAFF));
    }

    // ---------------------------------------------------------------------------------
    // GET /api/users/me - every authenticated role EXCEPT a guest
    // ---------------------------------------------------------------------------------

    /**
     * A guest used to reach this. ROLE_GUEST now means one thing: a visitor holding a day
     * pass, with no account behind it and a token whose subject is {@code visitor:<pass id>}.
     * The endpoint could only ever have answered 404, and refusing it is what makes "the pass
     * opens the campus map and nothing else" literally true.
     */
    @Test
    @DisplayName("GET /api/users/me: a guest is refused - a visitor has no profile")
    void getMe_guest_forbidden() throws Exception {
        mockMvc.perform(get("/api/users/me").with(callerWith(GUEST_ID, UserRole.ROLE_GUEST)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users/me: a student reaches their own profile")
    void getMe_student_succeeds() throws Exception {
        mockMvc.perform(get("/api/users/me").with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/users/me: a professor reaches their own profile")
    void getMe_professor_succeeds() throws Exception {
        mockMvc.perform(get("/api/users/me").with(callerWith(PROFESSOR_ID, UserRole.ROLE_PROFESSOR)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/users/me: an administrative employee reaches their own profile")
    void getMe_staff_succeeds() throws Exception {
        mockMvc.perform(get("/api/users/me").with(callerWith(STAFF_ID, UserRole.ROLE_STAFF)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/users/me: an admin reaches their own profile")
    void getMe_admin_succeeds() throws Exception {
        mockMvc.perform(get("/api/users/me").with(callerWith(ADMIN_ID, UserRole.ROLE_STAFF, UserRole.ROLE_ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/users/me: an anonymous caller is rejected")
    void getMe_anonymous_isRejected() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // PATCH /api/users/me - every authenticated role EXCEPT a guest
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("PATCH /api/users/me: a guest is refused - there is no profile to edit")
    void patchMe_guest_forbidden() throws Exception {
        mockMvc.perform(patch("/api/users/me").with(callerWith(GUEST_ID, UserRole.ROLE_GUEST))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_PATCH))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/users/me: a student can edit their own profile")
    void patchMe_student_succeeds() throws Exception {
        mockMvc.perform(patch("/api/users/me").with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_PATCH))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/users/me: a professor can edit their own profile")
    void patchMe_professor_succeeds() throws Exception {
        mockMvc.perform(patch("/api/users/me").with(callerWith(PROFESSOR_ID, UserRole.ROLE_PROFESSOR))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_PATCH))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/users/me: an administrative employee can edit their own profile")
    void patchMe_staff_succeeds() throws Exception {
        mockMvc.perform(patch("/api/users/me").with(callerWith(STAFF_ID, UserRole.ROLE_STAFF))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_PATCH))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/users/me: an admin can edit their own profile")
    void patchMe_admin_succeeds() throws Exception {
        mockMvc.perform(patch("/api/users/me").with(callerWith(ADMIN_ID, UserRole.ROLE_STAFF, UserRole.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_PATCH))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/users/me: an anonymous caller is rejected")
    void patchMe_anonymous_isRejected() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON).content(TRIVIAL_PATCH))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // GET /api/users - directory listing, ROLE_ADMIN only
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/users: an admin may list the directory")
    void listUsers_admin_succeeds() throws Exception {
        mockMvc.perform(get("/api/users").with(callerWith(ADMIN_ID, UserRole.ROLE_STAFF, UserRole.ROLE_ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/users: a guest is refused")
    void listUsers_guest_isForbidden() throws Exception {
        mockMvc.perform(get("/api/users").with(callerWith(GUEST_ID, UserRole.ROLE_GUEST)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users: a student is refused")
    void listUsers_student_isForbidden() throws Exception {
        mockMvc.perform(get("/api/users").with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users: a professor is refused")
    void listUsers_professor_isForbidden() throws Exception {
        mockMvc.perform(get("/api/users").with(callerWith(PROFESSOR_ID, UserRole.ROLE_PROFESSOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users: an administrative employee is refused")
    void listUsers_staff_isForbidden() throws Exception {
        mockMvc.perform(get("/api/users").with(callerWith(STAFF_ID, UserRole.ROLE_STAFF)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users: another permission, such as reception's, does not open the directory")
    void listUsers_reception_isForbidden() throws Exception {
        mockMvc.perform(get("/api/users")
                        .with(callerWith(STAFF_ID, UserRole.ROLE_STAFF, UserRole.ROLE_RECEPTION)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users: an anonymous caller is rejected")
    void listUsers_anonymous_isRejected() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // GET /api/users/{userId} - reading another account, ROLE_ADMIN only
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/users/{userId}: an admin may read another account")
    void getById_admin_succeeds() throws Exception {
        mockMvc.perform(get("/api/users/{userId}", STUDENT_ID)
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_STAFF, UserRole.ROLE_ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/users/{userId}: a guest is refused")
    void getById_guest_isForbidden() throws Exception {
        mockMvc.perform(get("/api/users/{userId}", STUDENT_ID)
                        .with(callerWith(GUEST_ID, UserRole.ROLE_GUEST)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users/{userId}: a student is refused even when the id is their own")
    void getById_studentReachingOwnProfile_isForbidden() throws Exception {
        mockMvc.perform(get("/api/users/{userId}", STUDENT_ID)
                        .with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users/{userId}: a student is refused when reaching another student's profile")
    void getById_studentReachingAnotherStudent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/users/{userId}", OTHER_STUDENT_ID)
                        .with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users/{userId}: a professor is refused")
    void getById_professor_isForbidden() throws Exception {
        mockMvc.perform(get("/api/users/{userId}", STUDENT_ID)
                        .with(callerWith(PROFESSOR_ID, UserRole.ROLE_PROFESSOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users/{userId}: an administrative employee is refused")
    void getById_staff_isForbidden() throws Exception {
        mockMvc.perform(get("/api/users/{userId}", STUDENT_ID)
                        .with(callerWith(STAFF_ID, UserRole.ROLE_STAFF)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users/{userId}: an anonymous caller is rejected")
    void getById_anonymous_isRejected() throws Exception {
        mockMvc.perform(get("/api/users/{userId}", STUDENT_ID))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // PATCH /api/users/{userId}/status - activation state, ROLE_ADMIN only
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("PATCH /api/users/{userId}/status: an admin may change another account's status")
    void setStatus_admin_succeeds() throws Exception {
        mockMvc.perform(patch("/api/users/{userId}/status", STUDENT_ID)
                        .with(callerWith(ADMIN_ID, UserRole.ROLE_STAFF, UserRole.ROLE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"active": false}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/users/{userId}/status: a guest is refused")
    void setStatus_guest_isForbidden() throws Exception {
        mockMvc.perform(patch("/api/users/{userId}/status", STUDENT_ID)
                        .with(callerWith(GUEST_ID, UserRole.ROLE_GUEST))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"active": false}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/users/{userId}/status: a student is refused")
    void setStatus_student_isForbidden() throws Exception {
        mockMvc.perform(patch("/api/users/{userId}/status", OTHER_STUDENT_ID)
                        .with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"active": false}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/users/{userId}/status: a professor is refused")
    void setStatus_professor_isForbidden() throws Exception {
        mockMvc.perform(patch("/api/users/{userId}/status", STUDENT_ID)
                        .with(callerWith(PROFESSOR_ID, UserRole.ROLE_PROFESSOR))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"active": false}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/users/{userId}/status: moderation's permission does not open activation")
    void setStatus_moderation_isForbidden() throws Exception {
        mockMvc.perform(patch("/api/users/{userId}/status", STUDENT_ID)
                        .with(callerWith(STAFF_ID, UserRole.ROLE_STAFF, UserRole.ROLE_MODERATION))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"active": false}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/users/{userId}/status: an anonymous caller is rejected")
    void setStatus_anonymous_isRejected() throws Exception {
        mockMvc.perform(patch("/api/users/{userId}/status", STUDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"active": false}"""))
                .andExpect(status().isUnauthorized());
    }
}

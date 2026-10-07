package co.edu.konradlorenz.kapp.user.web;

import co.edu.konradlorenz.kapp.common.feign.InternalTokenInterceptor;
import co.edu.konradlorenz.kapp.user.AbstractUserServiceTest;
import co.edu.konradlorenz.kapp.user.domain.StoredInstant;
import co.edu.konradlorenz.kapp.user.domain.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /internal/users}: the shared-secret gate and the idempotent, e-mail-keyed
 * upsert that lets a replayed sign-in call be safe.
 *
 * <p>No {@code jwt()} post-processor appears anywhere in this class. This endpoint carries
 * no user identity at all - authentication is the {@code X-Internal-Token} header, checked
 * by {@code InternalTokenAuthenticationFilter} before the request ever reaches Spring
 * Security's authorization layer.
 */
class InternalUserControllerTest extends AbstractUserServiceTest {

    private static final String EMAIL = "pepito.perez@konradlorenz.edu.co";

    private static final String STUDENT_BODY = """
            {"email": "Pepito.Perez@Konradlorenz.edu.co", "firstName": "Pepito",
             "lastName": "Perez Gomez", "roles": ["ROLE_STUDENT"]}""";

    @Test
    @DisplayName("a request with no token is rejected with 401")
    void upsert_missingToken_isRejected() throws Exception {
        mockMvc.perform(post("/internal/users")
                        .contentType(MediaType.APPLICATION_JSON).content(STUDENT_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a request with the wrong token is rejected with 401")
    void upsert_wrongToken_isRejected() throws Exception {
        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, "not-the-secret")
                        .contentType(MediaType.APPLICATION_JSON).content(STUDENT_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the correct token is accepted and creates the profile, a directory entry with nothing academic")
    void upsert_correctToken_createsProfile() throws Exception {
        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(STUDENT_BODY))
                .andExpect(status().isOk())
                // Lowercased on the way in, regardless of how auth-service sent it.
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.roles", contains("ROLE_STUDENT")))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$..avatarUrl").exists())
                .andExpect(jsonPath("$.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.academic").doesNotExist());
    }

    @Test
    @DisplayName("two calls with the e-mail differently capitalised yield exactly one document")
    void upsert_calledTwiceWithDifferentCapitalisation_yieldsOneDocument() throws Exception {
        upsert(STUDENT_BODY);
        upsert("""
                {"email": "pepito.perez@konradlorenz.edu.co", "firstName": "Pepito",
                 "lastName": "Perez Gomez", "roles": ["ROLE_STUDENT"]}""");

        long documents = mongoTemplate.count(Query.query(Criteria.where("email").is(EMAIL)), UserProfile.class);
        assertThat(documents).isEqualTo(1);
    }

    /**
     * A sign-in carries what Microsoft and auth-service know - the names and the roles - and
     * nothing else: the picture is the person's own, and whether the account is active is an
     * administrator's decision. Neither may be undone by the person signing in again.
     */
    @Test
    @DisplayName("the second call updates names and roles, and keeps the picture, the active flag and the creation date")
    void upsert_secondCall_updatesNamesAndRolesOnly() throws Exception {
        upsert(STUDENT_BODY);
        UserProfile created = mongoTemplate.findOne(Query.query(Criteria.where("email").is(EMAIL)), UserProfile.class);
        assertThat(created).isNotNull();
        save(created.withAvatarUrl("https://cdn.kapp.konradlorenz.edu.co/avatars/3f8a1c2e.jpg", StoredInstant.now())
                .withActive(false, StoredInstant.now()));

        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "pepito.perez@konradlorenz.edu.co",
                                 "firstName": "Pepito Andrés", "lastName": "Perez Gomez",
                                 "roles": ["ROLE_STAFF", "ROLE_ADMIN"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id()))
                .andExpect(jsonPath("$.firstName").value("Pepito Andrés"))
                .andExpect(jsonPath("$.roles", contains("ROLE_STAFF", "ROLE_ADMIN")))
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.kapp.konradlorenz.edu.co/avatars/3f8a1c2e.jpg"))
                .andExpect(jsonPath("$.active").value(false));

        UserProfile updated = reload(created.id());
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
    }

    @Test
    @DisplayName("a role sent twice is stored once")
    void upsert_repeatedRole_isStoredOnce() throws Exception {
        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "ana.ruiz@konradlorenz.edu.co", "firstName": "Ana",
                                 "lastName": "Ruiz Mejía",
                                 "roles": ["ROLE_STAFF", "ROLE_ADMIN", "ROLE_STAFF"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ROLE_STAFF", "ROLE_ADMIN")));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            '[]'                                  | must hold the profile role
            '["ROLE_ADMIN"]'                      | must hold exactly one of ROLE_STUDENT, ROLE_PROFESSOR or ROLE_STAFF
            '["ROLE_STUDENT", "ROLE_PROFESSOR"]'  | must hold exactly one of ROLE_STUDENT, ROLE_PROFESSOR or ROLE_STAFF
            '["ROLE_GUEST"]'                      | must not hold ROLE_GUEST
            '["ROLE_STUDENT", "ROLE_GUEST"]'      | must not hold ROLE_GUEST
            """)
    @DisplayName("roles must hold exactly one profile role, and never ROLE_GUEST")
    void upsert_badRoles_areRejected(String roles, String issue) throws Exception {
        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "maria.rodriguez@konradlorenz.edu.co", "firstName": "Maria",
                                 "lastName": "Rodriguez", "roles": %s}""".formatted(roles)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("roles"))
                .andExpect(jsonPath("$.details[0].issue").value(issue));

        assertThat(mongoTemplate.count(new Query(), UserProfile.class)).isZero();
    }

    /**
     * Before user 1.0, auth-service sent one {@code role} and an {@code academic} block. Such a call
     * must fail loudly rather than create a profile with no roles: the two services ship together.
     */
    @Test
    @DisplayName("a caller still on the old shape - one role and an academic block - is rejected")
    void upsert_oldShape_isRejected() throws Exception {
        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "pepito.perez@konradlorenz.edu.co", "firstName": "Pepito",
                                 "lastName": "Perez Gomez", "role": "ROLE_STUDENT",
                                 "academic": {"studentCode": "506900001", "programCode": "506",
                                              "pensumCode": "1015", "currentLevel": 1}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("roles"));
    }

    @Test
    @DisplayName("a role KApp does not know is rejected with 400")
    void upsert_unknownRole_isRejected() throws Exception {
        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "pepito.perez@konradlorenz.edu.co", "firstName": "Pepito",
                                 "lastName": "Perez Gomez", "roles": ["ROLE_STUDENT", "ROLE_DEAN"]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deleting a profile needs the token, and is the same 204 whether it existed or not")
    void delete_isGuardedAndIdempotent() throws Exception {
        save(student("to-delete", "borrar.esto@konradlorenz.edu.co", "Borrar", "Esto"));

        mockMvc.perform(delete("/internal/users/to-delete"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/internal/users/to-delete").header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN))
                .andExpect(status().isNoContent());
        assertThat(mongoTemplate.findById("to-delete", UserProfile.class)).isNull();
        mockMvc.perform(delete("/internal/users/to-delete").header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN))
                .andExpect(status().isNoContent());
    }

    private void upsert(String body) throws Exception {
        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }
}

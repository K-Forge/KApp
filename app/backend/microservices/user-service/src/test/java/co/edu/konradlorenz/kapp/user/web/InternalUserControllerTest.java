package co.edu.konradlorenz.kapp.user.web;

import co.edu.konradlorenz.kapp.common.feign.InternalTokenInterceptor;
import co.edu.konradlorenz.kapp.user.AbstractUserServiceTest;
import co.edu.konradlorenz.kapp.user.domain.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /internal/users}: the shared-secret gate and the idempotent, e-mail-keyed
 * upsert that lets a replayed registration call be safe.
 *
 * <p>No {@code jwt()} post-processor appears anywhere in this class. This endpoint carries
 * no user identity at all - authentication is the {@code X-Internal-Token} header, checked
 * by {@code InternalTokenAuthenticationFilter} before the request ever reaches Spring
 * Security's authorization layer.
 */
class InternalUserControllerTest extends AbstractUserServiceTest {

    private static final String STUDENT_BODY = """
            {"email": "Pepito.Perez@Konradlorenz.edu.co", "firstName": "Pepito",
             "lastName": "Perez Gomez", "role": "ROLE_STUDENT",
             "academic": {"studentCode": "506999999", "programCode": "506",
                          "pensumCode": "1015", "currentLevel": 1}}""";

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
    @DisplayName("the correct token is accepted and creates the profile")
    void upsert_correctToken_createsProfile() throws Exception {
        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(STUDENT_BODY))
                .andExpect(status().isOk())
                // Lowercased on the way in, regardless of how auth-service sent it.
                .andExpect(jsonPath("$.email").value("pepito.perez@konradlorenz.edu.co"))
                .andExpect(jsonPath("$.role").value("ROLE_STUDENT"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.academic.studentCode").value("506999999"));
    }

    @Test
    @DisplayName("two calls with the e-mail differently capitalised yield exactly one document")
    void upsert_calledTwiceWithDifferentCapitalisation_yieldsOneDocument() throws Exception {
        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "Pepito.Perez@Konradlorenz.edu.co",
                                 "firstName": "Pepito", "lastName": "Perez Gomez",
                                 "role": "ROLE_STUDENT",
                                 "academic": {"studentCode": "506999999", "programCode": "506",
                                              "pensumCode": "1015", "currentLevel": 1}}"""))
                .andExpect(status().isOk());

        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "pepito.perez@konradlorenz.edu.co",
                                 "firstName": "Pepito", "lastName": "Perez Gomez",
                                 "role": "ROLE_STUDENT",
                                 "academic": {"studentCode": "506999999", "programCode": "506",
                                              "pensumCode": "1015", "currentLevel": 2}}"""))
                .andExpect(status().isOk());

        long documents = mongoTemplate.count(
                Query.query(Criteria.where("email").is("pepito.perez@konradlorenz.edu.co")),
                UserProfile.class);
        assertThat(documents).isEqualTo(1);
    }

    @Test
    @DisplayName("the second call updates the existing profile rather than creating another")
    void upsert_secondCall_updatesExistingProfile() throws Exception {
        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(STUDENT_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "pepito.perez@konradlorenz.edu.co",
                                 "firstName": "Brian S.", "lastName": "Vargas C.",
                                 "role": "ROLE_STUDENT",
                                 "academic": {"studentCode": "506999999", "programCode": "506",
                                              "pensumCode": "1015", "currentLevel": 2}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Brian S."))
                .andExpect(jsonPath("$.academic.currentLevel").value(2));
    }

    @Test
    @DisplayName("a guest with an academic record is rejected with 400")
    void upsert_guestWithAcademic_isRejected() throws Exception {
        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "maria.rodriguez@gmail.com", "firstName": "Maria",
                                 "lastName": "Rodriguez", "role": "ROLE_GUEST",
                                 "academic": {"studentCode": "506999999", "programCode": "506",
                                              "pensumCode": "1015", "currentLevel": 1}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("academic"));
    }

    @Test
    @DisplayName("a student without an academic record is rejected with 400")
    void upsert_studentWithoutAcademic_isRejected() throws Exception {
        mockMvc.perform(post("/internal/users")
                        .header(InternalTokenInterceptor.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"email": "pepito.perez@konradlorenz.edu.co",
                                 "firstName": "Brian", "lastName": "Vargas",
                                 "role": "ROLE_STUDENT", "academic": null}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("academic"));
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
}

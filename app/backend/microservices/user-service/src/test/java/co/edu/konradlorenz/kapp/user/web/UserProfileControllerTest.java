package co.edu.konradlorenz.kapp.user.web;

import co.edu.konradlorenz.kapp.user.AbstractUserServiceTest;
import co.edu.konradlorenz.kapp.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Self-service access to {@code /api/users/me}: what the caller sees, and the one thing a person
 * may change about their own profile.
 *
 * <p>Who may call these endpoints at all is covered by {@link UserAuthorizationMatrixTest};
 * this class is about the shape of the response and the semantics of the patch.
 */
class UserProfileControllerTest extends AbstractUserServiceTest {

    private static final String STUDENT_ID = "b0000000-0000-0000-0000-000000000001";
    private static final String GUEST_ID = "b0000000-0000-0000-0000-000000000002";
    private static final String PROFESSOR_ID = "b0000000-0000-0000-0000-000000000003";
    private static final String ADMIN_ID = "b0000000-0000-0000-0000-000000000004";

    private static final String AVATAR = "https://cdn.kapp.konradlorenz.edu.co/avatars/3f8a1c2e.jpg";

    // ---------------------------------------------------------------------------------
    // GET /api/users/me
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("a student's own profile carries the academic block, read from SINU")
    void getMe_student_readsAcademicFromSinu() throws Exception {
        save(fullyPopulated(STUDENT_ID, "pepito.perez@konradlorenz.edu.co"));

        mockMvc.perform(get("/api/users/me").with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(STUDENT_ID))
                .andExpect(jsonPath("$.email").value("pepito.perez@konradlorenz.edu.co"))
                .andExpect(jsonPath("$.avatarUrl").value(AVATAR))
                .andExpect(jsonPath("$.roles", contains("ROLE_STUDENT")))
                .andExpect(jsonPath("$.academic.programCode").value("506"))
                .andExpect(jsonPath("$.academic.programName").value("Ingeniería de sistemas"))
                .andExpect(jsonPath("$.academic.pensumCode").value("1015"))
                .andExpect(jsonPath("$.academic.currentLevel").value(5))
                // KApp keeps none of these any more, so the profile cannot show them.
                .andExpect(jsonPath("$.academic.studentCode").doesNotExist())
                .andExpect(jsonPath("$.identification").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    /**
     * {@code academic} is an explicit null rather than a missing key, because the contract lists it
     * as required: a client tells "not a student" from "field not sent". SINU is not asked at all.
     */
    @Test
    @DisplayName("a professor's profile has academic explicitly null, and SINU is not asked")
    void getMe_professor_academicIsExplicitlyNull() throws Exception {
        save(professor(PROFESSOR_ID, "laura.gomez@konradlorenz.edu.co", "Laura Marcela", "Gómez Restrepo"));

        mockMvc.perform(get("/api/users/me").with(callerWith(PROFESSOR_ID, UserRole.ROLE_PROFESSOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ROLE_PROFESSOR")))
                .andExpect(jsonPath("$..academic").exists())
                .andExpect(jsonPath("$.academic").value(nullValue()))
                .andExpect(jsonPath("$.avatarUrl").value(nullValue()));

        verify(sinu, never()).student(any(), any());
    }

    @Test
    @DisplayName("an administrative employee who administers KApp sees both roles and no academic block")
    void getMe_staffAdmin_holdsBothRoles() throws Exception {
        save(admin(ADMIN_ID, "ana.ruiz@konradlorenz.edu.co", "Ana", "Ruiz Mejía"));

        mockMvc.perform(get("/api/users/me").with(callerWith(ADMIN_ID, UserRole.ROLE_STAFF, UserRole.ROLE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ROLE_STAFF", "ROLE_ADMIN")))
                .andExpect(jsonPath("$.academic").value(nullValue()));
    }

    /** A person's name and picture do not depend on the university's record. */
    @Test
    @DisplayName("with SINU out of reach a student still gets their profile, academic null")
    void getMe_sinuDown_servesProfileWithoutAcademic() throws Exception {
        save(fullyPopulated(STUDENT_ID, "pepito.perez@konradlorenz.edu.co"));
        doThrow(new IllegalStateException("SINU is not answering")).when(sinu).student(any(), any());

        mockMvc.perform(get("/api/users/me").with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Pepito"))
                .andExpect(jsonPath("$..academic").exists())
                .andExpect(jsonPath("$.academic").value(nullValue()));
    }

    @Test
    @DisplayName("a visitor's token is refused outright: there is no account behind it")
    void getMe_guest_forbidden() throws Exception {
        mockMvc.perform(get("/api/users/me").with(callerWith(GUEST_ID, UserRole.ROLE_GUEST)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------------------
    // PATCH /api/users/me - the picture
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("a person sets their picture, and it is stored")
    void patchMe_setsAvatar() throws Exception {
        save(student(STUDENT_ID, "pepito.perez@konradlorenz.edu.co", "Pepito", "Perez Gomez"));

        mockMvc.perform(patch("/api/users/me").with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"avatarUrl": "%s"}""".formatted(AVATAR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(AVATAR))
                // The response is the whole profile, academic block included.
                .andExpect(jsonPath("$.academic.pensumCode").value("1015"));

        assertThat(reload(STUDENT_ID).avatarUrl()).isEqualTo(AVATAR);
    }

    @Test
    @DisplayName("an explicit null clears the picture")
    void patchMe_explicitNull_clearsAvatar() throws Exception {
        save(fullyPopulated(STUDENT_ID, "pepito.perez@konradlorenz.edu.co"));

        mockMvc.perform(patch("/api/users/me").with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"avatarUrl": null}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.firstName").value("Pepito"));

        assertThat(reload(STUDENT_ID).avatarUrl()).isNull();
    }

    @Test
    @DisplayName("an empty body is rejected: avatarUrl is required, null being how to clear it")
    void patchMe_emptyBody_isRejected() throws Exception {
        save(fullyPopulated(STUDENT_ID, "pepito.perez@konradlorenz.edu.co"));

        mockMvc.perform(patch("/api/users/me").with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("avatarUrl"))
                .andExpect(jsonPath("$.details[0].issue").value("is required: send a URL, or null to clear it"));

        assertThat(reload(STUDENT_ID).avatarUrl()).isEqualTo(AVATAR);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            '"/avatars/3f8a1c2e.jpg"'   | must be an absolute URL
            42                          | must be a string
            '{"url": "https://x.co/a"}' | must be a string
            """)
    @DisplayName("a picture that is not an absolute URL is rejected with 400")
    void patchMe_rejectsMalformedAvatar(String value, String issue) throws Exception {
        save(student(STUDENT_ID, "pepito.perez@konradlorenz.edu.co", "Pepito", "Perez Gomez"));

        mockMvc.perform(patch("/api/users/me").with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\": " + value + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("avatarUrl"))
                .andExpect(jsonPath("$.details[0].issue").value(issue));
    }

    @Test
    @DisplayName("a picture URL over 500 characters is rejected with 400")
    void patchMe_rejectsAvatarOverCap() throws Exception {
        save(student(STUDENT_ID, "pepito.perez@konradlorenz.edu.co", "Pepito", "Perez Gomez"));
        String tooLong = "https://cdn.kapp.konradlorenz.edu.co/" + "a".repeat(500);

        mockMvc.perform(patch("/api/users/me").with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\": \"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].issue").value("must be at most 500 characters"));
    }

    @Test
    @DisplayName("a body that is not a JSON object is rejected with 400")
    void patchMe_rejectsNonObjectBody() throws Exception {
        save(student(STUDENT_ID, "pepito.perez@konradlorenz.edu.co", "Pepito", "Perez Gomez"));

        mockMvc.perform(patch("/api/users/me").with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request body must be a JSON object"));
    }

    // ---------------------------------------------------------------------------------
    // PATCH /api/users/me - everything else is refused, and says why
    // ---------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            firstName      | '"Updated"'                            | comes from Microsoft at every sign-in and cannot be changed here
            lastName       | '"Updated"'                            | comes from Microsoft at every sign-in and cannot be changed here
            email          | '"someone-else@konradlorenz.edu.co"'   | is owned by the authentication service and cannot be changed here
            roles          | '["ROLE_ADMIN"]'                       | is granted by the authentication service and cannot be changed here
            role           | '"ROLE_ADMIN"'                         | is granted by the authentication service and cannot be changed here
            active         | false                                  | is changed through PATCH /api/users/{userId}/status
            academic       | '{"currentLevel": 9}'                  | is read from SINU and cannot be changed here
            identification | '{"type": "CC", "number": "1032456789"}' | is not kept: KApp stores no identity document or phone number
            phone          | '"+573105551234"'                      | is not kept: KApp stores no identity document or phone number
            id             | '"some-other-id"'                      | is assigned by the server and cannot be changed
            nickname       | '"Pepe"'                               | is not a field of this resource
            """)
    @DisplayName("any field but avatarUrl is rejected with 400 and a reason, and nothing is written")
    void patchMe_rejectsEveryOtherField(String field, String value, String issue) throws Exception {
        save(fullyPopulated(STUDENT_ID, "pepito.perez@konradlorenz.edu.co"));

        mockMvc.perform(patch("/api/users/me").with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + field + "\": " + value + ", \"avatarUrl\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].field").value(field))
                .andExpect(jsonPath("$.details[0].issue").value(issue));

        // Refused as a whole: the picture sent alongside is not cleared either.
        assertThat(reload(STUDENT_ID).avatarUrl()).isEqualTo(AVATAR);
    }

    @Test
    @DisplayName("a client that sends back the whole profile it read is told every field it may not send")
    void patchMe_roundTrippedProfile_namesEveryField() throws Exception {
        save(fullyPopulated(STUDENT_ID, "pepito.perez@konradlorenz.edu.co"));

        mockMvc.perform(patch("/api/users/me").with(callerWith(STUDENT_ID, UserRole.ROLE_STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": "%s", "email": "pepito.perez@konradlorenz.edu.co",
                                 "firstName": "Pepito", "lastName": "Perez Gomez",
                                 "avatarUrl": "%s", "roles": ["ROLE_STUDENT"], "active": true,
                                 "academic": null}""".formatted(STUDENT_ID, AVATAR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field",
                        contains("id", "email", "firstName", "lastName", "roles", "active", "academic")));
    }
}

package co.edu.konradlorenz.kapp.auth;

import co.edu.konradlorenz.kapp.auth.client.InternalUserUpsert;
import co.edu.konradlorenz.kapp.auth.domain.Credential;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Password registration, for local development only: the domain, the duplicate, and the
 * failure modes {@code docs/INTEGRATION-NOTES.md} section 2 calls out. No code is needed since
 * #255, and the account has no profile role until an administrator gives it one.
 */
class RegistrationIntegrationTest extends AbstractAuthIntegrationTest {

    @Test
    @DisplayName("registers an account with no profile role and returns emailVerified=false")
    void institutionalRegistration_succeeds() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("pepito.perez@konradlorenz.edu.co")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("pepito.perez@konradlorenz.edu.co"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.userId").isNotEmpty());

        Credential saved = credentials.findByEmailIgnoreCase("pepito.perez@konradlorenz.edu.co")
                .orElseThrow();
        assertThat(saved.roles()).containsExactly("ROLE_UNASSIGNED");
        // Off by default: no SMTP relay, so the account must not be blocked on it.
        assertThat(saved.status()).isEqualTo(Credential.Status.ACTIVE);
        assertThat(saved.emailVerified()).isFalse();
    }

    @Test
    @DisplayName("lower-cases the e-mail before storing and checking for a duplicate")
    void institutionalRegistration_lowerCasesEmail() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("Pepito.Perez@KonradLorenz.edu.co")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("pepito.perez@konradlorenz.edu.co"));

        assertThat(credentials.existsByEmailIgnoreCase("pepito.perez@konradlorenz.edu.co")).isTrue();
    }

    @Test
    @DisplayName("creates the profile in user-service with the fields registration collected")
    void institutionalRegistration_sendsExpectedProfileToUserService() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("ana.gomez@konradlorenz.edu.co")))
                .andExpect(status().isCreated());

        var captor = org.mockito.ArgumentCaptor.forClass(InternalUserUpsert.class);
        verify(userProfileClient).upsert(captor.capture());
        InternalUserUpsert sent = captor.getValue();
        assertThat(sent.email()).isEqualTo("ana.gomez@konradlorenz.edu.co");
        // Since user 1.0 the profile carries the roles and nothing academic: user-service keeps
        // no student code, and reads a student's program, pensum and level from SINU.
        assertThat(sent.roles()).containsExactly("ROLE_UNASSIGNED");
        assertThat(sent.firstName()).isNotBlank();
    }

    @Test
    @DisplayName("rejects a non-institutional e-mail on the institutional endpoint")
    void institutionalRegistration_rejectsOutsideDomain() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("someone@gmail.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("email"));

        assertThat(credentials.existsByEmailIgnoreCase("someone@gmail.com")).isFalse();
    }

    /**
     * The allowed domain is a LIST, not one value: a development stack may accept a second
     * domain next to the institutional one. Without this test the list could collapse back to a
     * single domain unnoticed.
     */
    @Test
    @DisplayName("a second configured domain is accepted")
    void institutionalRegistration_acceptsSecondaryDomain() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("dev.account@kforge.dev")))
                .andExpect(status().isCreated());

        assertThat(credentials.existsByEmailIgnoreCase("dev.account@kforge.dev")).isTrue();
    }

    @Test
    @DisplayName("a domain that is not configured is still refused, and the message names both")
    void institutionalRegistration_rejectsUnconfiguredDomain() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("someone@kforge.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("email"))
                .andExpect(jsonPath("$.details[0].issue")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("@konradlorenz.edu.co"),
                                org.hamcrest.Matchers.containsString("@kforge.dev"))));

        assertThat(credentials.existsByEmailIgnoreCase("someone@kforge.com")).isFalse();
    }

    @Test
    @DisplayName("a duplicate e-mail is refused with 409")
    void institutionalRegistration_duplicateEmailReturns409() throws Exception {
        String payload = institutionalPayload("duplicate@konradlorenz.edu.co");

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("with user-service down nothing is created, and the answer is a 503")
    void institutionalRegistration_profileServiceDown_returns503() throws Exception {
        when(userProfileClient.upsert(any())).thenThrow(new RuntimeException("user-service unreachable"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("orphan@konradlorenz.edu.co")))
                .andExpect(status().isServiceUnavailable());

        assertThat(credentials.existsByEmailIgnoreCase("orphan@konradlorenz.edu.co")).isFalse();
    }

    /** A client that still sends a code from before #255 is not refused for it: the field is ignored. */
    @Test
    @DisplayName("a request that still carries an old invitation code registers all the same")
    void institutionalRegistration_ignoresAnOldCode() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "old.client@konradlorenz.edu.co", "password": "ExamplePassword123",
                                 "firstName": "Old", "lastName": "Client", "invitationCode": "KL-20262-STUDENT"}"""))
                .andExpect(status().isCreated());

        assertThat(credentials.findByEmailIgnoreCase("old.client@konradlorenz.edu.co").orElseThrow().roles())
                .containsExactly("ROLE_UNASSIGNED");
    }

    private static String institutionalPayload(String email) {
        return """
                {
                    "email": "%s",
                    "password": "ExamplePassword123",
                    "firstName": "Test",
                    "lastName": "User"
                }""".formatted(email);
    }
}

package co.edu.konradlorenz.kapp.auth;

import co.edu.konradlorenz.kapp.auth.domain.Credential;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Same registration and verification flow as {@link VerificationIntegrationTest}, but
 * with {@code kapp.auth.require-email-verification=true}. A distinct Spring context is
 * spun up for this property, per {@code @TestPropertySource}'s documented behaviour, but
 * it shares the one Testcontainers MongoDB instance inherited from
 * {@link AbstractAuthIntegrationTest}.
 */
@TestPropertySource(properties = "kapp.auth.require-email-verification=true")
class VerificationRequiredIntegrationTest extends AbstractAuthIntegrationTest {


    @Test
    @DisplayName("registration leaves the account pending, and login is refused until confirmed")
    void registration_createsPendingAccount_loginRefusedUntilVerified() throws Exception {
        String email = "pending@konradlorenz.edu.co";
        register(email, "506555666");

        Credential credential = credentials.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(credential.status()).isEqualTo(Credential.Status.PENDING_VERIFICATION);
        assertThat(credential.emailVerified()).isFalse();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(email)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("confirming the address activates the account and login then succeeds")
    void verify_activatesPendingAccount_thenLoginSucceeds() throws Exception {
        String email = "activate-me@konradlorenz.edu.co";
        register(email, "506555667");
        String token = lastVerificationTokenFor(email);

        mockMvc.perform(post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}""".formatted(token)))
                .andExpect(status().isNoContent());

        Credential credential = credentials.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(credential.status()).isEqualTo(Credential.Status.ACTIVE);
        assertThat(credential.emailVerified()).isTrue();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(credential.userId()));
    }

    private void register(String email, String studentCode) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "ExamplePassword123",
                                    "firstName": "Test",
                                    "lastName": "User",
                                    "studentCode": "%s",
                                    "programCode": "506"
                                }""".formatted(email, studentCode)))
                .andExpect(status().isCreated());
    }

    private static String loginPayload(String email) {
        return """
                {"email":"%s","password":"ExamplePassword123"}""".formatted(email);
    }
}

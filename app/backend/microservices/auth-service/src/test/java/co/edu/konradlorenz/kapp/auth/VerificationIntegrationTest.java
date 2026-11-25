package co.edu.konradlorenz.kapp.auth;

import co.edu.konradlorenz.kapp.auth.domain.Credential;
import co.edu.konradlorenz.kapp.auth.service.VerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E-mail verification with {@code kapp.auth.require-email-verification} at its default,
 * {@code false}: there is no SMTP relay in this test environment either, which is exactly
 * the situation the flag exists for. Registration still issues and "sends" (to the mocked
 * mailer) a token regardless of the flag; only whether an unverified account may sign in
 * depends on it. The flag-on behaviour lives in {@link VerificationRequiredIntegrationTest}.
 */
class VerificationIntegrationTest extends AbstractAuthIntegrationTest {


    @Test
    @DisplayName("with verification not required, a freshly registered account can log in unverified")
    void flagOff_unverifiedAccountCanStillLogIn() throws Exception {
        String email = "unverified@konradlorenz.edu.co";
        register(email, "506444555");

        Credential credential = credentials.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(credential.emailVerified()).isFalse();
        assertThat(credential.status()).isEqualTo(Credential.Status.ACTIVE);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(credential.userId()));
    }

    @Test
    @DisplayName("a valid verification token confirms the address, once")
    void verify_validToken_isSingleUse() throws Exception {
        String email = "confirm-me@konradlorenz.edu.co";
        register(email, "506444556");
        String token = lastVerificationTokenFor(email);

        mockMvc.perform(post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}""".formatted(token)))
                .andExpect(status().isNoContent());

        assertThat(credentials.findByEmailIgnoreCase(email).orElseThrow().emailVerified()).isTrue();

        // Replaying the same token must fail: it was consumed by the call above.
        mockMvc.perform(post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}""".formatted(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an unknown verification token is refused with 400")
    void verify_unknownToken_returns400() throws Exception {
        mockMvc.perform(post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"deadbeefdeadbeefdeadbeefdeadbeef"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("resend answers 202 for an address with no account, revealing nothing")
    void resend_unknownAddress_returns202() throws Exception {
        mockMvc.perform(post("/auth/verify/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@konradlorenz.edu.co"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(VerificationService.NEUTRAL_RESEND_MESSAGE));
    }

    @Test
    @DisplayName("resend answers 202 for a real, unverified address and issues a new token")
    void resend_knownUnverifiedAddress_returns202AndReissues() throws Exception {
        String email = "resend-me@konradlorenz.edu.co";
        register(email, "506444557");
        String firstToken = lastVerificationTokenFor(email);

        mockMvc.perform(post("/auth/verify/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}""".formatted(email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(VerificationService.NEUTRAL_RESEND_MESSAGE));

        verify(verificationMailer, atLeastOnce()).sendVerificationLink(eq(email), any());

        // The old link must stop working once a new one has been issued.
        mockMvc.perform(post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}""".formatted(firstToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("resend answers 202 for an already-verified address without sending anything new")
    void resend_alreadyVerifiedAddress_returns202WithoutReissuing() throws Exception {
        String email = "already-verified@konradlorenz.edu.co";
        register(email, "506444558");
        String token = lastVerificationTokenFor(email);
        mockMvc.perform(post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}""".formatted(token)))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.clearInvocations(verificationMailer);

        mockMvc.perform(post("/auth/verify/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}""".formatted(email)))
                .andExpect(status().isAccepted());

        verify(verificationMailer, never()).sendVerificationLink(eq(email), any());
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

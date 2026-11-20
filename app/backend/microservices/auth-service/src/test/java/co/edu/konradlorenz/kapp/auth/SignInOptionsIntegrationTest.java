package co.edu.konradlorenz.kapp.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /auth/sign-in-options}: what a client reads, without a token, to draw its sign-in page.
 */
class SignInOptionsIntegrationTest extends AbstractAuthIntegrationTest {

    @Test
    @DisplayName("with Microsoft not configured, microsoft is null; this suite's server has passwords on")
    void microsoftNotConfigured() throws Exception {
        mockMvc.perform(get("/auth/sign-in-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.microsoft").value(nullValue()))
                .andExpect(jsonPath("$.localSignIn").value(true));
    }

    @Nested
    @TestPropertySource(properties = {
            "kapp.auth.microsoft.issuers=http://localhost:8090/kapp,http://10.0.2.2:8090/kapp",
            "kapp.auth.microsoft.jwks-uri=http://fake-oidc:8080/kapp/jwks",
            "kapp.auth.microsoft.client-ids=kapp-app-dev,kapp-portal-dev",
            "kapp.auth.microsoft.portal-client-id=kapp-portal-dev",
            "kapp.auth.local-sign-in.enabled=false"
    })
    class WithMicrosoft extends AbstractAuthIntegrationTest {

        /** The first issuer is the one a browser reaches; the emulator's address is for the apps. */
        @Test
        @DisplayName("names the first issuer as the authority, and the portal's client id, without a token")
        void microsoftConfigured() throws Exception {
            mockMvc.perform(get("/auth/sign-in-options"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.microsoft.authority").value("http://localhost:8090/kapp"))
                    .andExpect(jsonPath("$.microsoft.clientId").value("kapp-portal-dev"))
                    .andExpect(jsonPath("$.localSignIn").value(false));
        }
    }
}

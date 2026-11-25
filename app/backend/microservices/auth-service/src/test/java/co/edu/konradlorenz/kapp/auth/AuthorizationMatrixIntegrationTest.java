package co.edu.konradlorenz.kapp.auth;

import co.edu.konradlorenz.kapp.auth.domain.Credential;
import co.edu.konradlorenz.kapp.common.security.KappRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every endpoint against every caller: anonymous, and a token for each of
 * {@code ROLE_GUEST}, {@code ROLE_STUDENT}, {@code ROLE_PROFESSOR}, {@code ROLE_ADMIN}.
 *
 * <h2>What "refused" means for this service</h2>
 * auth-service has no endpoint that discriminates between authenticated roles - it has
 * public endpoints, listed in {@code AuthSecurityConfig.PUBLIC_AUTH_PATHS}, and one
 * protected diagnostic ({@code GET /api/auth/ping}) that only demands a valid token,
 * whatever role it carries. The exhaustive matrix below reflects exactly that shape:
 * every public path is reachable by every caller including anonymous, and the one
 * protected path refuses anonymous alone. Each cell is its own {@code @Test} so a
 * regression names the exact combination that broke, rather than a loop reporting one
 * failure for all of them.
 *
 * <p>Role tokens are minted directly with {@link co.edu.konradlorenz.kapp.auth.jwt.JwtIssuer}
 * rather than through registration: {@code ROLE_ADMIN} is never reachable through
 * signup, so a real admin account cannot exist to log in with.
 */
class AuthorizationMatrixIntegrationTest extends AbstractAuthIntegrationTest {


    private String guestToken;
    private String studentToken;
    private String professorToken;
    private String adminToken;

    private static final String LOGIN_EMAIL = "matrix-login@konradlorenz.edu.co";
    private static final String LOGIN_PASSWORD = "ExamplePassword123";

    @BeforeEach
    void mintRoleTokens() {
        guestToken = bearerFor("matrix-guest", "matrix-guest@example.com", KappRoles.GUEST);
        studentToken = bearerFor("matrix-student", "matrix-student@konradlorenz.edu.co", KappRoles.STUDENT);
        professorToken = bearerFor("matrix-professor", "matrix-professor@konradlorenz.edu.co", KappRoles.PROFESSOR);
        adminToken = bearerFor("matrix-admin", "matrix-admin@konradlorenz.edu.co", KappRoles.ADMIN);

        seedCredential(LOGIN_EMAIL, LOGIN_PASSWORD, java.util.List.of(KappRoles.STUDENT),
                Credential.Status.ACTIVE, true);
    }

    // ---------------------------------------------------------------- GET /auth/health

    @Test
    @DisplayName("health: anonymous is admitted")
    void health_anonymous_ok() throws Exception {
        mockMvc.perform(get("/auth/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("health: GUEST is admitted")
    void health_guest_ok() throws Exception {
        mockMvc.perform(authed(get("/auth/health"), guestToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("health: STUDENT is admitted")
    void health_student_ok() throws Exception {
        mockMvc.perform(authed(get("/auth/health"), studentToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("health: PROFESSOR is admitted")
    void health_professor_ok() throws Exception {
        mockMvc.perform(authed(get("/auth/health"), professorToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("health: ADMIN is admitted")
    void health_admin_ok() throws Exception {
        mockMvc.perform(authed(get("/auth/health"), adminToken)).andExpect(status().isOk());
    }

    // ---------------------------------------------------------- GET /.well-known/jwks.json

    @Test
    @DisplayName("jwks: anonymous is admitted")
    void jwks_anonymous_ok() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("jwks: GUEST is admitted")
    void jwks_guest_ok() throws Exception {
        mockMvc.perform(authed(get("/.well-known/jwks.json"), guestToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("jwks: STUDENT is admitted")
    void jwks_student_ok() throws Exception {
        mockMvc.perform(authed(get("/.well-known/jwks.json"), studentToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("jwks: PROFESSOR is admitted")
    void jwks_professor_ok() throws Exception {
        mockMvc.perform(authed(get("/.well-known/jwks.json"), professorToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("jwks: ADMIN is admitted")
    void jwks_admin_ok() throws Exception {
        mockMvc.perform(authed(get("/.well-known/jwks.json"), adminToken)).andExpect(status().isOk());
    }

    // -------------------------------------------------------------------- POST /auth/login

    @Test
    @DisplayName("login: anonymous is admitted")
    void login_anonymous_ok() throws Exception {
        mockMvc.perform(loginRequest()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("login: a caller already carrying a GUEST token is still admitted")
    void login_guest_ok() throws Exception {
        mockMvc.perform(authed(loginRequest(), guestToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("login: a caller already carrying a STUDENT token is still admitted")
    void login_student_ok() throws Exception {
        mockMvc.perform(authed(loginRequest(), studentToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("login: a caller already carrying a PROFESSOR token is still admitted")
    void login_professor_ok() throws Exception {
        mockMvc.perform(authed(loginRequest(), professorToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("login: a caller already carrying an ADMIN token is still admitted")
    void login_admin_ok() throws Exception {
        mockMvc.perform(authed(loginRequest(), adminToken)).andExpect(status().isOk());
    }

    // ----------------------------------------------------------------- POST /auth/register

    @Test
    @DisplayName("register: anonymous is admitted")
    void register_anonymous_ok() throws Exception {
        mockMvc.perform(registerRequest("matrix-anon@konradlorenz.edu.co")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("register: a caller carrying a GUEST token is still admitted")
    void register_guestToken_ok() throws Exception {
        mockMvc.perform(authed(registerRequest("matrix-reg-guest@konradlorenz.edu.co"), guestToken))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("register: a caller carrying a STUDENT token is still admitted")
    void register_studentToken_ok() throws Exception {
        mockMvc.perform(authed(registerRequest("matrix-reg-student@konradlorenz.edu.co"), studentToken))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("register: a caller carrying a PROFESSOR token is still admitted")
    void register_professorToken_ok() throws Exception {
        mockMvc.perform(authed(registerRequest("matrix-reg-professor@konradlorenz.edu.co"), professorToken))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("register: a caller carrying an ADMIN token is still admitted")
    void register_adminToken_ok() throws Exception {
        mockMvc.perform(authed(registerRequest("matrix-reg-admin@konradlorenz.edu.co"), adminToken))
                .andExpect(status().isCreated());
    }

    // ----------------------------------------------------------- POST /auth/register/guest

    // ------------------------------------------------------------------- POST /auth/verify

    @Test
    @DisplayName("verify: anonymous reaches the handler (business 400, not a security refusal)")
    void verify_anonymous_reachesHandler() throws Exception {
        mockMvc.perform(verifyRequest()).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("verify: GUEST reaches the handler")
    void verify_guest_reachesHandler() throws Exception {
        mockMvc.perform(authed(verifyRequest(), guestToken)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("verify: STUDENT reaches the handler")
    void verify_student_reachesHandler() throws Exception {
        mockMvc.perform(authed(verifyRequest(), studentToken)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("verify: PROFESSOR reaches the handler")
    void verify_professor_reachesHandler() throws Exception {
        mockMvc.perform(authed(verifyRequest(), professorToken)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("verify: ADMIN reaches the handler")
    void verify_admin_reachesHandler() throws Exception {
        mockMvc.perform(authed(verifyRequest(), adminToken)).andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------- POST /auth/verify/resend

    @Test
    @DisplayName("verify/resend: anonymous is admitted")
    void resend_anonymous_ok() throws Exception {
        mockMvc.perform(resendRequest()).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("verify/resend: GUEST is admitted")
    void resend_guest_ok() throws Exception {
        mockMvc.perform(authed(resendRequest(), guestToken)).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("verify/resend: STUDENT is admitted")
    void resend_student_ok() throws Exception {
        mockMvc.perform(authed(resendRequest(), studentToken)).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("verify/resend: PROFESSOR is admitted")
    void resend_professor_ok() throws Exception {
        mockMvc.perform(authed(resendRequest(), professorToken)).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("verify/resend: ADMIN is admitted")
    void resend_admin_ok() throws Exception {
        mockMvc.perform(authed(resendRequest(), adminToken)).andExpect(status().isAccepted());
    }

    // ------------------------------------------------------------------ GET /api/auth/ping
    // The only endpoint in this service that actually refuses a caller by authentication
    // state. It does not discriminate further by role, so every authenticated role passes.

    @Test
    @DisplayName("ping: anonymous is REFUSED")
    void ping_anonymous_refused() throws Exception {
        mockMvc.perform(get("/api/auth/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ping: GUEST is admitted")
    void ping_guest_ok() throws Exception {
        mockMvc.perform(authed(get("/api/auth/ping"), guestToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ping: STUDENT is admitted")
    void ping_student_ok() throws Exception {
        mockMvc.perform(authed(get("/api/auth/ping"), studentToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ping: PROFESSOR is admitted")
    void ping_professor_ok() throws Exception {
        mockMvc.perform(authed(get("/api/auth/ping"), professorToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ping: ADMIN is admitted")
    void ping_admin_ok() throws Exception {
        mockMvc.perform(authed(get("/api/auth/ping"), adminToken)).andExpect(status().isOk());
    }

    // --------------------------------------------------------------------------- helpers

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, String bearer) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer);
    }

    private static MockHttpServletRequestBuilder loginRequest() {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(LOGIN_EMAIL, LOGIN_PASSWORD));
    }

    private static MockHttpServletRequestBuilder registerRequest(String email) {
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {
                    "email": "%s",
                    "password": "ExamplePassword123",
                    "firstName": "Matrix",
                    "lastName": "Case",
                    "studentCode": "506777888",
                    "programCode": "506"
                }""".formatted(email));
    }

    private static MockHttpServletRequestBuilder guestRegisterRequest(String email) {
        return post("/auth/register/guest").contentType(MediaType.APPLICATION_JSON).content("""
                {
                    "email": "%s",
                    "password": "VisitanteSegura!26",
                    "firstName": "Matrix",
                    "lastName": "Guest"
                }""".formatted(email));
    }

    private static MockHttpServletRequestBuilder verifyRequest() {
        return post("/auth/verify").contentType(MediaType.APPLICATION_JSON).content("""
                {"token":"not-a-real-verification-token"}""");
    }

    private static MockHttpServletRequestBuilder resendRequest() {
        return post("/auth/verify/resend").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"no-such-account@konradlorenz.edu.co"}""");
    }

    /**
     * Guest registration was removed when the visitor day pass replaced it: it accepted any
     * e-mail address, created a real account, and left it behind forever, for somebody who
     * was on campus for an afternoon.
     *
     * <p>This stays in the matrix as an entry that must be REFUSED, rather than being deleted
     * with the feature. A path that used to be public and is now gone is exactly the kind of
     * thing a later change re-opens by accident.
     */
    @Test
    @DisplayName("registerGuest: the endpoint is gone, and no role can reach it")
    void registerGuest_isGoneForEveryRole() throws Exception {
        String body = """
                {"email":"visitante@gmail.com","password":"Password123!",
                 "firstName":"Vis","lastName":"Itante"}
                """;

        // Anonymously it is a 401: the path is no longer public, so the filter chain refuses
        // it before the dispatcher looks for a handler.
        mockMvc.perform(post("/auth/register/guest")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        // With any valid token it is a 404, which is what proves the handler is really gone.
        for (String role : java.util.List.of("ROLE_GUEST", "ROLE_STUDENT", "ROLE_PROFESSOR",
                "ROLE_ADMIN")) {
            mockMvc.perform(post("/auth/register/guest")
                            .header("Authorization", bearerFor("u-" + role, "u@x.co", role))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound());
        }
    }
}

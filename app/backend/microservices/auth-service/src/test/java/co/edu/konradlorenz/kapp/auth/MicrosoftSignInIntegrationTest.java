package co.edu.konradlorenz.kapp.auth;

import co.edu.konradlorenz.kapp.auth.client.InternalUserUpsert;
import co.edu.konradlorenz.kapp.auth.client.UserProfileView;
import co.edu.konradlorenz.kapp.auth.domain.Credential;
import co.edu.konradlorenz.kapp.auth.error.MicrosoftSignInUnavailableException;
import co.edu.konradlorenz.kapp.auth.identity.MicrosoftIdTokenVerifier;
import co.edu.konradlorenz.kapp.auth.identity.MicrosoftIdentity;
import co.edu.konradlorenz.kapp.auth.identity.MicrosoftIdentity.DirectoryProfile;
import co.edu.konradlorenz.kapp.common.error.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /auth/microsoft}: the account a Microsoft identity signs in to, its roles, its
 * profile, and the portal's rule. Checking the token itself is {@code MicrosoftIdTokenVerifierTest}'s;
 * here the check is stood in for, so each test says who the token is about.
 */
@TestPropertySource(properties = "kapp.auth.first-admins=Ana.Ruiz@konradlorenz.edu.co")
class MicrosoftSignInIntegrationTest extends AbstractAuthIntegrationTest {

    private static final String TOKEN = "a-microsoft-id-token-for-the-test";
    private static final String OID = "0f1e2d3c-4b5a-4968-8776-655443322110";
    private static final String EMAIL = "pepito.perez@konradlorenz.edu.co";

    @MockitoBean
    private MicrosoftIdTokenVerifier verifier;

    @BeforeEach
    void theTokenIsAboutAStudent() {
        mongoTemplate.getCollection("refresh_tokens").deleteMany(new org.bson.Document());
        signsInAs(OID, EMAIL, DirectoryProfile.STUDENT);
    }

    // ---------------------------------------------------------------------------------
    // The first sign-in, and the next ones
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("the first sign-in creates the account and its profile, and returns KApp's tokens")
    void firstSignIn_createsTheAccount() throws Exception {
        String body = signIn("APP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ROLE_STUDENT")))
                .andExpect(jsonPath("$.refreshToken", startsWith("rt_")))
                .andExpect(jsonPath("$.refreshExpiresIn").value(2_592_000))
                .andReturn().getResponse().getContentAsString();
        String userId = objectMapper.readTree(body).get("userId").asText();

        Credential account = credentials.findByExternalId(OID).orElseThrow();
        assertThat(account.userId()).isEqualTo(userId);
        assertThat(account.email()).isEqualTo(EMAIL);
        assertThat(account.provider()).isEqualTo(Credential.Provider.ENTRA_ID);
        assertThat(account.passwordHash()).isNull();
        assertThat(account.emailVerified()).isTrue();
        verify(userProfileClient).upsert(new InternalUserUpsert(EMAIL, "Pepito", "Perez Gomez", List.of("ROLE_STUDENT")));
    }

    @Test
    @DisplayName("signing in again finds the same account, refreshes the names and follows the directory's profile")
    void nextSignIn_refreshesNamesAndProfile() throws Exception {
        signIn("APP").andExpect(status().isOk());
        when(verifier.verify(TOKEN)).thenReturn(new MicrosoftIdentity(OID, EMAIL, "Pepito Andrés", "Perez Gomez",
                "Pepito Andrés Perez Gomez", Optional.of(DirectoryProfile.PROFESSOR)));

        signIn("APP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ROLE_PROFESSOR")));

        assertThat(credentials.count()).isEqualTo(1);
        verify(userProfileClient).upsert(new InternalUserUpsert(EMAIL, "Pepito Andrés", "Perez Gomez",
                List.of("ROLE_PROFESSOR")));
    }

    @Test
    @DisplayName("an account the directory does not classify signs in as staff")
    void unclassified_isStaff() throws Exception {
        signsInAs(OID, EMAIL, null);

        signIn("APP").andExpect(status().isOk()).andExpect(jsonPath("$.roles", contains("ROLE_STAFF")));
    }

    @Test
    @DisplayName("a profile role an administrator chose stays, whatever the directory says")
    void administratorsProfileRole_stays() throws Exception {
        String userId = userIdOf(signIn("APP").andExpect(status().isOk()));
        Credential admin = seedCredential("otro.admin@konradlorenz.edu.co", "AdminPassword2026",
                List.of("ROLE_STAFF", "ROLE_ADMIN"), Credential.Status.ACTIVE, true);
        mockMvc.perform(put("/auth/admin/accounts/{id}/roles", userId)
                        .header("Authorization", bearerFor(admin.userId(), admin.email(), "ROLE_STAFF", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"profileRole": "ROLE_PROFESSOR", "permissions": ["ROLE_MODERATION"]}"""))
                .andExpect(status().isOk());

        signIn("APP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ROLE_PROFESSOR", "ROLE_MODERATION")));
    }

    @Test
    @DisplayName("permissions an administrator granted are kept at every sign-in")
    void permissions_areKept() throws Exception {
        String userId = userIdOf(signIn("APP").andExpect(status().isOk()));
        Credential account = credentials.findByUserId(userId).orElseThrow();
        credentials.save(account.withRoles(List.of("ROLE_STUDENT", "ROLE_WELLBEING"), false, java.time.Instant.now()));

        signIn("APP").andExpect(status().isOk()).andExpect(jsonPath("$.roles", contains("ROLE_STUDENT", "ROLE_WELLBEING")));
    }

    @Test
    @DisplayName("one of the first administrators gets ROLE_ADMIN, address compared ignoring case")
    void firstAdmin_getsAdmin() throws Exception {
        signsInAs("1a2b3c4d-0000-4000-8000-000000000009", "ana.ruiz@konradlorenz.edu.co", DirectoryProfile.STAFF);

        signIn("PORTAL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ROLE_STAFF", "ROLE_ADMIN")))
                .andExpect(jsonPath("$.refreshExpiresIn").value(43_200));
    }

    @Test
    @DisplayName("an account made locally before is adopted by its address, keeping its id")
    void localAccount_isAdopted() throws Exception {
        Credential local = seedCredential(EMAIL, "ExamplePassword123", List.of("ROLE_STUDENT"),
                Credential.Status.ACTIVE, false);
        when(userProfileClient.upsert(any())).thenReturn(new UserProfileView(local.userId(), EMAIL));

        signIn("APP").andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(local.userId()));

        Credential adopted = credentials.findByUserId(local.userId()).orElseThrow();
        assertThat(adopted.externalId()).isEqualTo(OID);
        assertThat(adopted.emailVerified()).isTrue();
        assertThat(credentials.count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------
    // Refusals
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("the portal refuses an account with no permission, and issues nothing")
    void portal_withoutPermission_isForbidden() throws Exception {
        signIn("PORTAL")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.details[0].field").value("client"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        assertThat(mongoTemplate.getCollection("refresh_tokens").countDocuments()).isZero();
    }

    @Test
    @DisplayName("a deactivated account is refused with 403")
    void deactivated_isForbidden() throws Exception {
        String userId = userIdOf(signIn("APP").andExpect(status().isOk()));
        credentials.save(credentials.findByUserId(userId).orElseThrow()
                .withSignInAllowed(false, java.time.Instant.now()));

        signIn("APP")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account is deactivated"));
    }

    @Test
    @DisplayName("a token that does not hold is a 401, and no account is made")
    void invalidToken_isUnauthorized() throws Exception {
        when(verifier.verify(TOKEN)).thenThrow(new InvalidCredentialsException("The Microsoft sign-in could not be verified"));

        signIn("APP").andExpect(status().isUnauthorized());

        assertThat(credentials.count()).isZero();
        verify(userProfileClient, never()).upsert(any());
    }

    @Test
    @DisplayName("Microsoft not configured, or out of reach, is a 503")
    void microsoftUnavailable_is503() throws Exception {
        when(verifier.verify(TOKEN)).thenThrow(new MicrosoftSignInUnavailableException("not configured"));

        signIn("APP").andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("with user-service down nothing is created, and the answer is a 503")
    void userServiceDown_is503() throws Exception {
        when(userProfileClient.upsert(any())).thenThrow(new IllegalStateException("user-service is down"));

        signIn("APP").andExpect(status().isServiceUnavailable());

        assertThat(credentials.count()).isZero();
    }

    @Test
    @DisplayName("a request without a client, or with a token too short to be one, is a 400")
    void malformedRequest_isBadRequest() throws Exception {
        mockMvc.perform(post("/auth/microsoft").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": \"" + TOKEN + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/microsoft").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\": \"short\", \"client\": \"APP\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("names fall back to the display name when the token has no given or family name")
    void names_fallBackToTheDisplayName() throws Exception {
        when(verifier.verify(TOKEN)).thenReturn(new MicrosoftIdentity(OID, EMAIL, null, null,
                "Pepito Perez Gomez", Optional.of(DirectoryProfile.STUDENT)));

        signIn("APP").andExpect(status().isOk());

        ArgumentCaptor<InternalUserUpsert> sent = ArgumentCaptor.forClass(InternalUserUpsert.class);
        verify(userProfileClient, atLeastOnce()).upsert(sent.capture());
        assertThat(sent.getValue().firstName()).isEqualTo("Pepito");
        assertThat(sent.getValue().lastName()).isEqualTo("Perez Gomez");
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private void signsInAs(String oid, String email, DirectoryProfile profile) {
        when(verifier.verify(TOKEN)).thenReturn(new MicrosoftIdentity(oid, email, "Pepito", "Perez Gomez",
                "Pepito Perez Gomez", Optional.ofNullable(profile)));
    }

    private ResultActions signIn(String client) throws Exception {
        return mockMvc.perform(post("/auth/microsoft").contentType(MediaType.APPLICATION_JSON).content("""
                {"idToken": "%s", "client": "%s"}""".formatted(TOKEN, client)));
    }

    private String userIdOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("userId").asText();
    }
}

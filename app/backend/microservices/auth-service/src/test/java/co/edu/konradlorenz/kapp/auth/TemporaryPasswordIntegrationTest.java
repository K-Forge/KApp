package co.edu.konradlorenz.kapp.auth;

import co.edu.konradlorenz.kapp.auth.client.InternalUserUpsert;
import co.edu.konradlorenz.kapp.auth.domain.Credential;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Accounts an administrator creates, with a temporary password the person replaces at first
 * sign-in: {@code /auth/admin/accounts} and {@code /auth/password}.
 *
 * <p>What is worth pinning: the temporary password signs nobody in by itself, replacing it
 * signs the person in, it dies after its week, and issuing a new one ends the old password at
 * once.
 */
class TemporaryPasswordIntegrationTest extends AbstractAuthIntegrationTest {

    /** Three groups of four from the alphabet with no O/0 or I/1. */
    private static final String TEMPORARY = "^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$";

    private String adminToken() {
        return bearerFor("admin-1", "admin" + INSTITUTIONAL_DOMAIN, "ROLE_ADMIN");
    }

    private String createProfessor(String local) throws Exception {
        return mockMvc.perform(post("/auth/admin/accounts")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s%s","firstName":"Ana","lastName":"Ruiz","role":"ROLE_PROFESSOR"}
                                """.formatted(local, INSTITUTIONAL_DOMAIN)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private org.springframework.test.web.servlet.ResultActions change(String email, String current, String next) throws Exception {
        return mockMvc.perform(post("/auth/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","currentPassword":"%s","newPassword":"%s"}
                        """.formatted(email, current, next)));
    }

    @Test
    @DisplayName("an admin creates an account: its profile, and a temporary password shown once")
    void createsAnAccountWithATemporaryPassword() throws Exception {
        String created = createProfessor("ana.ruiz");

        assertThat((String) JsonPath.read(created, "$.temporaryPassword")).matches(TEMPORARY);
        assertThat((String) JsonPath.read(created, "$.role")).isEqualTo("ROLE_PROFESSOR");
        Instant expiresAt = Instant.parse(JsonPath.read(created, "$.expiresAt"));
        assertThat(expiresAt).isBetween(Instant.now().plus(6, ChronoUnit.DAYS), Instant.now().plus(8, ChronoUnit.DAYS));

        Credential stored = credentials.findByEmailIgnoreCase("ana.ruiz" + INSTITUTIONAL_DOMAIN).orElseThrow();
        assertThat(stored.mustChangePassword()).isTrue();
        assertThat(stored.status()).isEqualTo(Credential.Status.ACTIVE);
        assertThat(stored.emailVerified()).isFalse();
        // Only the hash is kept.
        assertThat(stored.passwordHash()).doesNotContain((String) JsonPath.read(created, "$.temporaryPassword"));

        ArgumentCaptor<InternalUserUpsert> profile = ArgumentCaptor.forClass(InternalUserUpsert.class);
        verify(userProfileClient, atLeastOnce()).upsert(profile.capture());
        assertThat(profile.getValue().role()).isEqualTo("ROLE_PROFESSOR");
        assertThat(profile.getValue().firstName()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("the temporary password signs nobody in: login asks for a new one")
    void theTemporaryPasswordAsksForANewOne() throws Exception {
        String created = createProfessor("temp.only");
        String temporary = JsonPath.read(created, "$.temporaryPassword");

        login("temp.only" + INSTITUTIONAL_DOMAIN, temporary)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.details[0].field").value("newPassword"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    @DisplayName("replacing it signs the person in, and from then on only the new password works")
    void replacingItSignsIn() throws Exception {
        String email = "first.signin" + INSTITUTIONAL_DOMAIN;
        String temporary = JsonPath.read(createProfessor("first.signin"), "$.temporaryPassword");

        change(email, temporary, "MyOwnPassword2026")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_PROFESSOR"));

        login(email, "MyOwnPassword2026").andExpect(status().isOk());
        login(email, temporary).andExpect(status().isUnauthorized());
        assertThat(credentials.findByEmailIgnoreCase(email).orElseThrow().mustChangePassword()).isFalse();
    }

    @Test
    @DisplayName("the new password must differ from the temporary one")
    void theNewPasswordMustDiffer() throws Exception {
        String email = "same.again" + INSTITUTIONAL_DOMAIN;
        String temporary = JsonPath.read(createProfessor("same.again"), "$.temporaryPassword");

        change(email, temporary, temporary)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("newPassword"));
    }

    @Test
    @DisplayName("past its week the temporary password is just a wrong password, to login and to replacing it")
    void anExpiredTemporaryPasswordIsRefused() throws Exception {
        String email = "too.late" + INSTITUTIONAL_DOMAIN;
        String temporary = JsonPath.read(createProfessor("too.late"), "$.temporaryPassword");
        Credential stored = credentials.findByEmailIgnoreCase(email).orElseThrow();
        Instant longAgo = Instant.now().minus(8, ChronoUnit.DAYS);
        credentials.save(stored.withTemporaryPassword(stored.passwordHash(), longAgo.plus(7, ChronoUnit.DAYS), longAgo));

        login(email, temporary).andExpect(status().isUnauthorized());
        change(email, temporary, "MyOwnPassword2026").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a new temporary password ends the one the person had at once")
    void issuingANewOneEndsTheOldPassword() throws Exception {
        String email = "forgot.it" + INSTITUTIONAL_DOMAIN;
        Credential own = seedCredential(email, "RememberedPassword1", List.of("ROLE_STUDENT"),
                Credential.Status.ACTIVE, false);

        String issued = mockMvc.perform(post("/auth/admin/accounts/{id}/temporary-password", own.userId())
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String temporary = JsonPath.read(issued, "$.temporaryPassword");

        login(email, "RememberedPassword1").andExpect(status().isUnauthorized());
        login(email, temporary).andExpect(status().isForbidden());
        change(email, temporary, "ANewPassword2026").andExpect(status().isOk());
    }

    @Test
    @DisplayName("only an admin creates accounts, never an admin's, and a student needs its codes")
    void theRulesOfCreating() throws Exception {
        String body = """
                {"email":"x%s","firstName":"X","lastName":"Y","role":"%s"%s}
                """;
        mockMvc.perform(post("/auth/admin/accounts")
                        .header("Authorization", bearerFor("prof-1", "p" + INSTITUTIONAL_DOMAIN, "ROLE_PROFESSOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(INSTITUTIONAL_DOMAIN, "ROLE_PROFESSOR", "")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/auth/admin/accounts")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(INSTITUTIONAL_DOMAIN, "ROLE_ADMIN", "")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/admin/accounts")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(INSTITUTIONAL_DOMAIN, "ROLE_STUDENT", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("studentCode"));
        mockMvc.perform(post("/auth/admin/accounts")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(INSTITUTIONAL_DOMAIN, "ROLE_STUDENT",
                                ",\"studentCode\":\"506900200\",\"programCode\":\"506\"")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/admin/accounts")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted("@gmail.com", "ROLE_PROFESSOR", "")))
                .andExpect(status().isBadRequest());
    }

    // ── A client that takes only some accounts: the admin portal ──────────────────

    private org.springframework.test.web.servlet.ResultActions loginAsAdminPortal(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","allowedRoles":["ROLE_ADMIN"]}
                        """.formatted(email, password)));
    }

    @Test
    @DisplayName("the admin portal refuses a professor's temporary password before offering to replace it")
    void aPortalForAdminsNeverReplacesAnotherKindsPassword() throws Exception {
        String email = "not.an.admin" + INSTITUTIONAL_DOMAIN;
        String temporary = JsonPath.read(createProfessor("not.an.admin"), "$.temporaryPassword");

        loginAsAdminPortal(email, temporary)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.details[0].field").value("allowedRoles"));
        mockMvc.perform(post("/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","currentPassword":"%s","newPassword":"MyOwnPassword2026","allowedRoles":["ROLE_ADMIN"]}
                                """.formatted(email, temporary)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.details[0].field").value("allowedRoles"));

        // Nothing changed: the temporary password is still the one, still to be replaced.
        assertThat(credentials.findByEmailIgnoreCase(email).orElseThrow().mustChangePassword()).isTrue();
        login(email, temporary).andExpect(status().isForbidden()).andExpect(jsonPath("$.details[0].field").value("newPassword"));
    }

    @Test
    @DisplayName("an administrator with a temporary password goes on to choose one in the portal")
    void anAdminWithATemporaryPasswordChoosesOne() throws Exception {
        String email = "admin.forgot" + INSTITUTIONAL_DOMAIN;
        Credential admin = seedCredential(email, "AdminPassword2026", List.of("ROLE_ADMIN"), Credential.Status.ACTIVE, false);
        String temporary = JsonPath.read(mockMvc.perform(post("/auth/admin/accounts/{id}/temporary-password", admin.userId())
                        .header("Authorization", bearerFor("admin-2", "other" + INSTITUTIONAL_DOMAIN, "ROLE_ADMIN")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.temporaryPassword");

        loginAsAdminPortal(email, temporary)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.details[0].field").value("newPassword"));
    }

    @Test
    @DisplayName("a student's program is read from the first three digits of its student code")
    void theProgramComesFromTheStudentCode() throws Exception {
        mockMvc.perform(post("/auth/admin/accounts")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"codigo.solo%s","firstName":"Pepe","lastName":"Veras","role":"ROLE_STUDENT","studentCode":"506232730"}
                                """.formatted(INSTITUTIONAL_DOMAIN)))
                .andExpect(status().isCreated());

        ArgumentCaptor<InternalUserUpsert> profile = ArgumentCaptor.forClass(InternalUserUpsert.class);
        verify(userProfileClient, atLeastOnce()).upsert(profile.capture());
        assertThat(profile.getValue().academic().studentCode()).isEqualTo("506232730");
        assertThat(profile.getValue().academic().programCode()).isEqualTo("506");
    }

    @Test
    @DisplayName("an admin deletes an account and its profile, but never their own or another admin's")
    void deletingAnAccount() throws Exception {
        String email = "borrar" + INSTITUTIONAL_DOMAIN;
        Credential student = seedCredential(email, "StudentPassword1", List.of("ROLE_STUDENT"), Credential.Status.ACTIVE, false);
        Credential otherAdmin = seedCredential("otro.admin" + INSTITUTIONAL_DOMAIN, "AdminPassword2026", List.of("ROLE_ADMIN"), Credential.Status.ACTIVE, false);

        mockMvc.perform(delete("/auth/admin/accounts/{id}", student.userId())
                        .header("Authorization", bearerFor("prof-1", "p" + INSTITUTIONAL_DOMAIN, "ROLE_PROFESSOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/auth/admin/accounts/{id}", student.userId()).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        assertThat(credentials.findByEmailIgnoreCase(email)).isEmpty();
        verify(userProfileClient).delete(eq(student.userId()));
        login(email, "StudentPassword1").andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/auth/admin/accounts/{id}", otherAdmin.userId()).header("Authorization", adminToken()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/auth/admin/accounts/{id}", "admin-1").header("Authorization", adminToken()))
                .andExpect(status().isBadRequest());
        assertThat(credentials.findByUserId(otherAdmin.userId())).isPresent();
    }
}

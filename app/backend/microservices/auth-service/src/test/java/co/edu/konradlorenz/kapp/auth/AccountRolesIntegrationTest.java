package co.edu.konradlorenz.kapp.auth;

import co.edu.konradlorenz.kapp.auth.client.InternalUserRoles;
import co.edu.konradlorenz.kapp.auth.domain.Credential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PUT /auth/admin/accounts/{userId}/roles}: an administrator setting an account's profile
 * role and permissions, and what that does to the account, the directory and the next token.
 */
class AccountRolesIntegrationTest extends AbstractAuthIntegrationTest {

    private static final String PASSWORD = "ExamplePassword123";

    private Credential admin;
    private Credential professor;

    @BeforeEach
    void seedAccounts() {
        admin = seedCredential("ana.ruiz" + INSTITUTIONAL_DOMAIN, PASSWORD, List.of("ROLE_STAFF", "ROLE_ADMIN"),
                Credential.Status.ACTIVE, true);
        professor = seedCredential("laura.gomez" + INSTITUTIONAL_DOMAIN, PASSWORD, List.of("ROLE_PROFESSOR"),
                Credential.Status.ACTIVE, true);
    }

    @Test
    @DisplayName("an administrator replaces an account's roles: the profile role first, then the permissions")
    void admin_setsRoles() throws Exception {
        setRoles(professor.userId(), asAdmin(), """
                {"profileRole": "ROLE_PROFESSOR", "permissions": ["ROLE_MODERATION", "ROLE_ADMIN"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(professor.userId()))
                .andExpect(jsonPath("$.roles", contains("ROLE_PROFESSOR", "ROLE_ADMIN", "ROLE_MODERATION")));

        assertThat(credentials.findByUserId(professor.userId()).orElseThrow().roles())
                .containsExactly("ROLE_PROFESSOR", "ROLE_ADMIN", "ROLE_MODERATION");
        verify(userProfileClient).setRoles(professor.userId(),
                new InternalUserRoles(List.of("ROLE_PROFESSOR", "ROLE_ADMIN", "ROLE_MODERATION")));
    }

    @Test
    @DisplayName("an empty list takes every permission away")
    void emptyPermissions_takeThemAway() throws Exception {
        setRoles(professor.userId(), asAdmin(), """
                {"profileRole": "ROLE_STAFF", "permissions": ["ROLE_RECEPTION"]}""").andExpect(status().isOk());

        setRoles(professor.userId(), asAdmin(), """
                {"profileRole": "ROLE_STAFF", "permissions": []}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ROLE_STAFF")));
    }

    /** A permission granted or taken away reaches the person's tokens within the hour. */
    @Test
    @DisplayName("the new roles reach the person's token at the next renewal")
    void newRoles_reachTheNextToken() throws Exception {
        String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "laura.gomez@konradlorenz.edu.co", "password": "%s"}""".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refresh = objectMapper.readTree(body).get("refreshToken").asText();

        setRoles(professor.userId(), asAdmin(), """
                {"profileRole": "ROLE_PROFESSOR", "permissions": ["ROLE_WELLBEING"]}""").andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ROLE_PROFESSOR", "ROLE_WELLBEING")));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            not a profile role         | '{"profileRole": "ROLE_ADMIN", "permissions": []}'                                 | profileRole
            a profile role as a permission | '{"profileRole": "ROLE_STAFF", "permissions": ["ROLE_STUDENT"]}'             | permissions
            not a permission           | '{"profileRole": "ROLE_STAFF", "permissions": ["ROLE_DEAN"]}'                      | permissions
            a permission twice         | '{"profileRole": "ROLE_STAFF", "permissions": ["ROLE_ADMIN", "ROLE_ADMIN"]}'       | permissions
            no profile role            | '{"permissions": []}'                                                              | profileRole
            no permissions list        | '{"profileRole": "ROLE_STAFF"}'                                                    | permissions
            """)
    @DisplayName("roles the contract does not allow are a 400, and nothing changes")
    void invalidRoles_areRejected(String reason, String body, String field) throws Exception {
        setRoles(professor.userId(), asAdmin(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value(field));

        assertThat(credentials.findByUserId(professor.userId()).orElseThrow().roles()).containsExactly("ROLE_PROFESSOR");
        verify(userProfileClient, never()).setRoles(any(), any());
    }

    @Test
    @DisplayName("an administrator cannot take ROLE_ADMIN away from themselves")
    void admin_cannotDropOwnAdmin() throws Exception {
        setRoles(admin.userId(), asAdmin(), """
                {"profileRole": "ROLE_STAFF", "permissions": ["ROLE_RECEPTION"]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("permissions"));

        assertThat(credentials.findByUserId(admin.userId()).orElseThrow().roles()).contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("an administrator may change their own roles while keeping ROLE_ADMIN")
    void admin_changesOwnRolesKeepingAdmin() throws Exception {
        setRoles(admin.userId(), asAdmin(), """
                {"profileRole": "ROLE_STAFF", "permissions": ["ROLE_ADMIN", "ROLE_RECEPTION"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ROLE_STAFF", "ROLE_ADMIN", "ROLE_RECEPTION")));
    }

    @Test
    @DisplayName("an account that does not exist is a 404")
    void unknownAccount_is404() throws Exception {
        setRoles("nobody", asAdmin(), """
                {"profileRole": "ROLE_STAFF", "permissions": []}""")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("only an administrator sets roles: another permission does not do")
    void nonAdmins_areForbidden() throws Exception {
        for (String[] roles : List.of(new String[]{"ROLE_STAFF", "ROLE_RECEPTION"}, new String[]{"ROLE_STUDENT"},
                new String[]{"ROLE_PROFESSOR", "ROLE_MODERATION"})) {
            setRoles(professor.userId(), bearerFor("x-1", "x" + INSTITUTIONAL_DOMAIN, roles), """
                    {"profileRole": "ROLE_STAFF", "permissions": ["ROLE_ADMIN"]}""")
                    .andExpect(status().isForbidden());
        }
        assertThat(credentials.findByUserId(professor.userId()).orElseThrow().roles()).containsExactly("ROLE_PROFESSOR");
    }

    /** The roles are the account's either way; sending them again finishes the directory's half. */
    @Test
    @DisplayName("with user-service down the roles are saved, and the answer says to send them again")
    void userServiceDown_rolesSavedAnd503() throws Exception {
        doThrow(new IllegalStateException("user-service is down"))
                .when(userProfileClient).setRoles(eq(professor.userId()), any());

        setRoles(professor.userId(), asAdmin(), """
                {"profileRole": "ROLE_PROFESSOR", "permissions": ["ROLE_MODERATION"]}""")
                .andExpect(status().isServiceUnavailable());

        assertThat(credentials.findByUserId(professor.userId()).orElseThrow().roles())
                .containsExactly("ROLE_PROFESSOR", "ROLE_MODERATION");
    }

    private String asAdmin() {
        return bearerFor(admin.userId(), admin.email(), "ROLE_STAFF", "ROLE_ADMIN");
    }

    private ResultActions setRoles(String userId, String bearer, String body) throws Exception {
        return mockMvc.perform(put("/auth/admin/accounts/{userId}/roles", userId)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}

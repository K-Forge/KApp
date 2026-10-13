package co.edu.konradlorenz.kapp.auth;

import co.edu.konradlorenz.kapp.auth.domain.Credential;
import co.edu.konradlorenz.kapp.auth.domain.RefreshToken;
import co.edu.konradlorenz.kapp.auth.service.SessionService;
import co.edu.konradlorenz.kapp.auth.service.VerificationService;
import co.edu.konradlorenz.kapp.common.error.InvalidCredentialsException;
import com.fasterxml.jackson.databind.JsonNode;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sessions: the refresh token every sign-in returns, renewing with it, reusing it, signing out, and
 * what deactivating or deleting an account does to its sessions.
 */
class RefreshTokenIntegrationTest extends AbstractAuthIntegrationTest {

    private static final String EMAIL = "pepito.perez" + INSTITUTIONAL_DOMAIN;
    private static final String PASSWORD = "ExamplePassword123";
    private static final String SESSION_ENDED = "The session has ended. Sign in again";

    @Autowired
    private SessionService sessions;

    @Autowired
    private JwtDecoder jwtDecoder;

    private Credential account;

    @BeforeEach
    void seedAccount() {
        mongoTemplate.remove(new Query(), RefreshToken.class);
        account = seedCredential(EMAIL, PASSWORD, List.of("ROLE_STUDENT"), Credential.Status.ACTIVE, true);
    }

    // ---------------------------------------------------------------------------------
    // Signing in
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("signing in returns a refresh token that lasts 30 days in the apps")
    void signIn_returnsRefreshToken() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken", startsWith("rt_")))
                .andExpect(jsonPath("$.refreshExpiresIn").value(2_592_000))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    /** A copy of the collection opens no session: only the hash is kept. */
    @Test
    @DisplayName("only the token's hash is stored, never the token")
    void onlyTheHashIsStored() throws Exception {
        String token = signIn();

        List<Document> stored = mongoTemplate.getCollection(RefreshToken.COLLECTION).find().into(new ArrayList<>());
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getString("tokenHash")).isEqualTo(VerificationService.hash(token));
        assertThat(stored.getFirst().toJson()).doesNotContain(token);
        assertThat(stored.getFirst().getString("userId")).isEqualTo(account.userId());
        assertThat(stored.getFirst().getString("client")).isEqualTo("APP");
    }

    @Test
    @DisplayName("replacing a temporary password signs in with a refresh token too")
    void passwordChange_returnsRefreshToken() throws Exception {
        mockMvc.perform(post("/auth/password").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "currentPassword": "%s", "newPassword": "AnotherPassword456"}"""
                        .formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken", startsWith("rt_")));
    }

    @Test
    @DisplayName("the portal's session lasts a working day")
    void portalSession_lastsTwelveHours() {
        var opened = sessions.open(jwtIssuer.issue(account.userId(), EMAIL, account.roles()), RefreshToken.Client.PORTAL);

        assertThat(opened.refresh().expiresIn()).isEqualTo(Duration.ofHours(12).toSeconds());
        RefreshToken stored = mongoTemplate.findOne(
                Query.query(Criteria.where("tokenHash").is(VerificationService.hash(opened.refresh().token()))),
                RefreshToken.class);
        assertThat(stored).isNotNull();
        assertThat(Duration.between(stored.createdAt(), stored.expiresAt())).isEqualTo(Duration.ofHours(12));
    }

    // ---------------------------------------------------------------------------------
    // Renewing
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("a renewal returns a new access token and a new refresh token, which renews in turn")
    void refresh_rotates() throws Exception {
        String first = signIn();

        JsonNode renewed = body(refresh(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(account.userId()))
                .andExpect(jsonPath("$.roles", contains("ROLE_STUDENT")))
                .andExpect(jsonPath("$.refreshExpiresIn").value(2_592_000)));
        String second = renewed.get("refreshToken").asText();

        assertThat(second).startsWith("rt_").isNotEqualTo(first);
        assertThat(jwtDecoder.decode(renewed.get("accessToken").asText()).getSubject()).isEqualTo(account.userId());
        refresh(second).andExpect(status().isOk());
    }

    /** Somebody holds a copy, and nothing here can tell the person from them: both sign in again. */
    @Test
    @DisplayName("a refresh token used twice revokes its whole family")
    void refresh_reuse_revokesTheFamily() throws Exception {
        String first = signIn();
        String second = refreshToken(refresh(first).andExpect(status().isOk()));

        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(SESSION_ENDED));
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a reuse ends only the session it happened in: the same person's other device keeps renewing")
    void refresh_reuse_leavesOtherSessionsAlone() throws Exception {
        String phone = signIn();
        String tablet = signIn();
        refresh(phone).andExpect(status().isOk());

        refresh(phone).andExpect(status().isUnauthorized());

        refresh(tablet).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a refresh token past its expiry renews nothing")
    void refresh_expired_isRefused() throws Exception {
        String token = signIn();
        mongoTemplate.getCollection(RefreshToken.COLLECTION).updateOne(
                new Document("tokenHash", VerificationService.hash(token)),
                new Document("$set", new Document("expiresAt", Date.from(Instant.now().minusSeconds(1)))));

        refresh(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(SESSION_ENDED));
    }

    @Test
    @DisplayName("an unknown refresh token is refused with the same 401")
    void refresh_unknown_isRefused() throws Exception {
        refresh("rt_" + "x".repeat(43))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(SESSION_ENDED));
    }

    @Test
    @DisplayName("a refresh token too short to be one is a 400")
    void refresh_malformed_isBadRequest() throws Exception {
        refresh("rt_short").andExpect(status().isBadRequest());
    }

    /** A permission granted or taken away reaches the client at its next renewal, within the hour. */
    @Test
    @DisplayName("a renewal signs the roles the account has now")
    void refresh_carriesCurrentRoles() throws Exception {
        String token = signIn();
        credentials.save(new Credential(account.id(), account.userId(), account.email(), account.passwordHash(),
                List.of("ROLE_STAFF", "ROLE_ADMIN"), account.status(), account.emailVerified(), account.provider(),
                null, null, account.createdAt(), Instant.now(), null, null));

        JsonNode renewed = body(refresh(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("ROLE_STAFF", "ROLE_ADMIN"))));

        assertThat(jwtDecoder.decode(renewed.get("accessToken").asText()).getClaimAsStringList("roles"))
                .containsExactly("ROLE_STAFF", "ROLE_ADMIN");
    }

    // ---------------------------------------------------------------------------------
    // Deactivating and deleting an account
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("deactivating an account refuses its renewals with 403, and revokes its sessions for good")
    void suspension_revokesEverySession() throws Exception {
        String token = signIn();

        setSignInAllowed(false);
        refresh(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account is deactivated"));

        // Reactivated, the account signs in again, but the old session stays ended.
        setSignInAllowed(true);
        refresh(token).andExpect(status().isUnauthorized());
        signIn();
    }

    @Test
    @DisplayName("deleting an account removes its sessions")
    void deletion_removesSessions() throws Exception {
        String token = signIn();
        String admin = bearerFor("a0000000-0000-0000-0000-000000000001", "admin" + INSTITUTIONAL_DOMAIN, "ROLE_ADMIN");

        mockMvc.perform(delete("/auth/admin/accounts/{id}", account.userId()).header("Authorization", admin))
                .andExpect(status().isNoContent());

        assertThat(mongoTemplate.count(Query.query(Criteria.where("userId").is(account.userId())), RefreshToken.class))
                .isZero();
        refresh(token).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------
    // Signing out
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("signing out revokes the session: its refresh token renews nothing afterwards")
    void logout_revokesTheSession() throws Exception {
        String first = signIn();
        String second = refreshToken(refresh(first).andExpect(status().isOk()));

        logout(second).andExpect(status().isNoContent());

        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("signing out is always 204, even with a token already used, revoked or unknown")
    void logout_isAlways204() throws Exception {
        String first = signIn();
        refresh(first).andExpect(status().isOk());

        logout(first).andExpect(status().isNoContent());
        logout(first).andExpect(status().isNoContent());
        logout("rt_" + "y".repeat(43)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("signing out without a refresh token is a 400")
    void logout_withoutToken_isBadRequest() throws Exception {
        mockMvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------------
    // Races and storage
    // ---------------------------------------------------------------------------------

    /**
     * Two renewals with one token at the same moment: at most one gets a session, and the family
     * ends revoked whichever way they interleave, so the winner's new token cannot escape it.
     */
    @Test
    @DisplayName("two renewals racing with one token leave at most one winner, and the family revoked")
    void refresh_race_endsTheFamily() throws Exception {
        String token = signIn();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> attempt = () -> {
            start.await();
            try {
                return sessions.renew(token);
            } catch (InvalidCredentialsException refused) {
                return refused;
            }
        };
        Future<Object> one = pool.submit(attempt);
        Future<Object> two = pool.submit(attempt);
        start.countDown();
        List<Object> outcomes = List.of(one.get(), two.get());
        pool.shutdown();

        List<SessionService.SessionTokens> winners = outcomes.stream()
                .filter(SessionService.SessionTokens.class::isInstance)
                .map(SessionService.SessionTokens.class::cast)
                .toList();
        assertThat(winners).hasSizeLessThanOrEqualTo(1);
        assertThat(mongoTemplate.count(Query.query(Criteria.where("revokedAt").is(null)), RefreshToken.class))
                .isZero();
        for (SessionService.SessionTokens winner : winners) {
            refresh(winner.refresh().token()).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("refresh tokens are indexed by hash, uniquely, and removed by the database once expired")
    void indexes() {
        List<IndexInfo> indexes = mongoTemplate.indexOps(RefreshToken.COLLECTION).getIndexInfo();

        assertThat(indexes).extracting(IndexInfo::getName)
                .contains("uk_refresh_token_hash", "ix_refresh_token_family", "ix_refresh_token_user",
                        "ttl_refresh_token_expiry");
        assertThat(indexes).filteredOn(i -> i.getName().equals("uk_refresh_token_hash"))
                .allMatch(IndexInfo::isUnique);
        assertThat(indexes).filteredOn(i -> i.getName().equals("ttl_refresh_token_expiry"))
                .allSatisfy(i -> assertThat(i.getExpireAfter()).contains(Duration.ZERO));
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private String credentials() {
        return """
                {"email": "%s", "password": "%s"}""".formatted(EMAIL, PASSWORD);
    }

    private String signIn() throws Exception {
        return refreshToken(mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials()))
                .andExpect(status().isOk()));
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"" + token + "\"}"));
    }

    private ResultActions logout(String token) throws Exception {
        return mockMvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"" + token + "\"}"));
    }

    private void setSignInAllowed(boolean allowed) throws Exception {
        mockMvc.perform(post("/internal/credentials/{userId}/status", account.userId())
                        .header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": " + allowed + "}"))
                .andExpect(status().isNoContent());
    }

    private String refreshToken(ResultActions result) throws Exception {
        return body(result).get("refreshToken").asText();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}

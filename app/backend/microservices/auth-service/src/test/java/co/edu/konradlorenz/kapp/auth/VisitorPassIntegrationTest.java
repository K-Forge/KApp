package co.edu.konradlorenz.kapp.auth;

import co.edu.konradlorenz.kapp.auth.domain.VisitorPass;
import co.edu.konradlorenz.kapp.auth.domain.VisitorPassRepository;
import co.edu.konradlorenz.kapp.auth.service.VisitorPassService;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The visitor day pass: reception mints a code, a visitor redeems it with an identity
 * document, and the token that comes back opens the campus map and nothing else.
 *
 * <p>Two properties carry the weight. The token must be an ordinary {@code ROLE_GUEST}
 * token, because that is what makes "map only" the authorization matrix every service
 * already enforces rather than a second mechanism that can drift. And the register must
 * carry a purge deadline, because it holds identity documents and Ley 1581 gives that data
 * a shelf life.
 */
class VisitorPassIntegrationTest extends AbstractAuthIntegrationTest {

    @Autowired
    private VisitorPassRepository passes;

    private String adminToken;

    @BeforeEach
    void clearPasses() {
        passes.deleteAll();
        adminToken = bearerFor("recepcion-1", "recepcion@konradlorenz.edu.co", "ROLE_ADMIN");
    }

    // ── Issuing ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reception mints a pass and gets back a code to read out")
    void issuingReturnsACode() throws Exception {
        mockMvc.perform(post("/auth/admin/visitor-passes")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Feria de programas\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.redeemed").value(false))
                .andExpect(jsonPath("$.notes").value("Feria de programas"))
                .andExpect(jsonPath("$.documentNumber").doesNotExist());
    }

    @Test
    @DisplayName("a pass can be minted with no body at all: issuing one is a single click")
    void issuingNeedsNoBody() throws Exception {
        mockMvc.perform(post("/auth/admin/visitor-passes").header("Authorization", adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("the code avoids the characters that get misheard across a counter")
    void codesAvoidAmbiguousCharacters() throws Exception {
        for (int i = 0; i < 12; i++) {
            String code = issuePass();
            assertThat(code).startsWith("KL-V-");
            assertThat(code.replace("KL-V-", "").replace("-", ""))
                    .as("a code is read aloud and typed by somebody who has never seen it")
                    .doesNotContain("I").doesNotContain("O")
                    .doesNotContain("0").doesNotContain("1");
        }
    }

    @Test
    @DisplayName("every pass carries the deadline at which the whole record disappears")
    void everyPassCarriesAPurgeDeadline() throws Exception {
        String code = issuePass();
        VisitorPass pass = passes.findByCode(code).orElseThrow();

        assertThat(pass.purgeAt())
                .as("a register of identity documents must have a shelf life - Ley 1581")
                .isCloseTo(Instant.now().plus(Duration.ofDays(30)), within10Minutes());
    }

    // ── Redeeming ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("redeeming records the document and returns a guest token")
    void redeemingReturnsAGuestToken() throws Exception {
        String code = issuePass();

        mockMvc.perform(post("/auth/visitor-passes/{code}/redeem", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redemptionBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_GUEST"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                // A pass is for today: nothing renews it, and both fields say so rather than
                // being left out.
                .andExpect(jsonPath("$..refreshToken").exists())
                .andExpect(jsonPath("$.refreshToken").value(nullValue()))
                .andExpect(jsonPath("$.refreshExpiresIn").value(nullValue()));
    }

    /**
     * The property the whole feature rests on. `ROLE_GUEST` is refused by semaphore-service
     * and schedule-service and allowed by map-service, and those rules already have their own
     * tests. Issuing a plain guest token is what makes "the pass opens the map and nothing
     * else" true by construction rather than by a second mechanism nobody keeps in step.
     */
    @Test
    @DisplayName("the token carries ROLE_GUEST and nothing else, so it is map-only by construction")
    void tokenIsAnOrdinaryGuestToken() throws Exception {
        String token = redeemNewPass();
        var claims = SignedJWT.parse(token).getJWTClaimsSet();

        assertThat(claims.getStringListClaim("roles")).containsExactly("ROLE_GUEST");
    }

    @Test
    @DisplayName("the token's subject is the pass, not a person: there is no account behind it")
    void tokenSubjectIsThePass() throws Exception {
        String token = redeemNewPass();
        var claims = SignedJWT.parse(token).getJWTClaimsSet();

        assertThat(claims.getSubject()).startsWith("visitor:");
        assertThat(claims.getClaim("email"))
                .as("inventing an address would put something that looks like an account into logs")
                .isNull();
    }

    @Test
    @DisplayName("the token lasts 24 hours, not the one hour an ordinary session lasts")
    void tokenLastsADay() throws Exception {
        String code = issuePass();
        String body = mockMvc.perform(post("/auth/visitor-passes/{code}/redeem", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redemptionBody()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long expiresIn = objectMapper.readTree(body).get("expiresIn").asLong();
        assertThat(expiresIn).isEqualTo(Duration.ofHours(24).toSeconds());
    }

    @Test
    @DisplayName("redeeming stores the visitor's document, which is the point of the register")
    void redemptionIsRecorded() throws Exception {
        String code = issuePass();
        redeem(code);

        VisitorPass pass = passes.findByCode(code).orElseThrow();
        assertThat(pass.redeemed()).isTrue();
        assertThat(pass.documentNumber()).isEqualTo("1032456789");
        assertThat(pass.visitorName()).isEqualTo("Pepita Pérez Ríos");
        assertThat(pass.documentType().name()).isEqualTo("CC");
        assertThat(pass.accessExpiresAt())
                .isCloseTo(Instant.now().plus(Duration.ofHours(24)), within10Minutes());
    }

    @Test
    @DisplayName("no account is created: a visitor is here for an afternoon")
    void redemptionCreatesNoAccount() throws Exception {
        long before = credentials.count();
        redeemNewPass();

        assertThat(credentials.count())
                .as("an account outlives the visit; that is what this feature replaced")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("a pass can be redeemed once; the second attempt is 409")
    void aPassIsSingleUse() throws Exception {
        String code = issuePass();
        redeem(code);

        mockMvc.perform(post("/auth/visitor-passes/{code}/redeem", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redemptionBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0].issue").value("already-redeemed"));
    }

    @Test
    @DisplayName("an expired pass is refused, and says so rather than looking like a wrong code")
    void anExpiredPassIsRefused() throws Exception {
        String code = issuePass();
        expire(code);

        mockMvc.perform(post("/auth/visitor-passes/{code}/redeem", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redemptionBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0].issue").value("expired"));
    }

    @Test
    @DisplayName("an unknown code is 404")
    void unknownCodeIs404() throws Exception {
        mockMvc.perform(post("/auth/visitor-passes/{code}/redeem", "KL-V-ZZZZ-ZZZZ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redemptionBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("redeeming without a document is refused: an anonymous pass is an account with extra steps")
    void documentIsRequired() throws Exception {
        String code = issuePass();

        mockMvc.perform(post("/auth/visitor-passes/{code}/redeem", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitorName\":\"Sin documento\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a document number with punctuation is refused rather than stored as typed")
    void documentNumberIsConstrained() throws Exception {
        String code = issuePass();

        mockMvc.perform(post("/auth/visitor-passes/{code}/redeem", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentType":"CC","documentNumber":"1.032.456-789",
                                 "visitorName":"Con puntos"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── The register ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reception can read the register, including who redeemed each pass")
    void receptionReadsTheRegister() throws Exception {
        String code = issuePass();
        redeem(code);

        mockMvc.perform(get("/auth/admin/visitor-passes").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].visitorName").value("Pepita Pérez Ríos"))
                .andExpect(jsonPath("$[0].documentNumber").value("1032456789"));
    }

    @Test
    @DisplayName("the register can be filtered to the passes that were actually used")
    void registerCanBeFiltered() throws Exception {
        redeem(issuePass());
        issuePass();

        mockMvc.perform(get("/auth/admin/visitor-passes").param("redeemed", "true")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/auth/admin/visitor-passes").param("redeemed", "false")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("an unredeemed pass can be revoked")
    void unredeemedPassCanBeRevoked() throws Exception {
        String code = issuePass();

        mockMvc.perform(delete("/auth/admin/visitor-passes/{code}", code)
                        .header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        assertThat(passes.findByCode(code)).isEmpty();
    }

    @Test
    @DisplayName("a redeemed pass cannot be deleted: it is a visit record, and it leaves on its own")
    void redeemedPassCannotBeDeleted() throws Exception {
        String code = issuePass();
        redeem(code);

        mockMvc.perform(delete("/auth/admin/visitor-passes/{code}", code)
                        .header("Authorization", adminToken))
                .andExpect(status().isConflict());

        assertThat(passes.findByCode(code))
                .as("a register reception could quietly edit is not a register")
                .isPresent();
    }

    // ── Authorization ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("minting a pass needs ROLE_ADMIN")
    void issuingRequiresAdmin() throws Exception {
        for (String role : List.of("ROLE_STUDENT", "ROLE_PROFESSOR", "ROLE_GUEST")) {
            mockMvc.perform(post("/auth/admin/visitor-passes")
                            .header("Authorization", bearerFor("u", "u@x.co", role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("the register is not readable without a token, nor by a visitor")
    void registerIsNotPublic() throws Exception {
        mockMvc.perform(get("/auth/admin/visitor-passes"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/auth/admin/visitor-passes")
                        .header("Authorization", bearerFor("v", null, "ROLE_GUEST")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("redeeming is public: a visitor has no account to authenticate with")
    void redeemingIsPublic() throws Exception {
        String code = issuePass();

        mockMvc.perform(post("/auth/visitor-passes/{code}/redeem", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redemptionBody()))
                .andExpect(status().isOk());
    }

    /**
     * Both halves matter. Anonymously the path is a 401, not a 404, because it is no longer
     * one of the public auth paths and the filter chain refuses it before the dispatcher ever
     * looks for a handler - which is the right order, and incidentally does not confirm
     * whether the endpoint ever existed. With a valid token it is a 404, which is what proves
     * the handler is genuinely gone rather than merely protected.
     */
    @Test
    @DisplayName("guest registration is gone: the pass replaced it")
    void guestRegistrationNoLongerExists() throws Exception {
        String body = """
                {"email":"visitante@gmail.com","password":"Password123!",
                 "firstName":"Vis","lastName":"Itante"}
                """;

        mockMvc.perform(post("/auth/register/guest")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/register/guest")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private String issuePass() throws Exception {
        String body = mockMvc.perform(post("/auth/admin/visitor-passes")
                        .header("Authorization", adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("code").asText();
    }

    private String redemptionBody() {
        return """
                {"documentType":"CC","documentNumber":"1032456789",
                 "visitorName":"Pepita Pérez Ríos"}
                """;
    }

    private void redeem(String code) throws Exception {
        mockMvc.perform(post("/auth/visitor-passes/{code}/redeem", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redemptionBody()))
                .andExpect(status().isOk());
    }

    private String redeemNewPass() throws Exception {
        String code = issuePass();
        String body = mockMvc.perform(post("/auth/visitor-passes/{code}/redeem", code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redemptionBody()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    /** Moves a pass's redemption window into the past without waiting twelve hours for it. */
    private void expire(String code) {
        VisitorPass pass = passes.findByCode(code).orElseThrow();
        passes.save(new VisitorPass(pass.id(), pass.code(), pass.issuedBy(), pass.notes(),
                pass.createdAt(), Instant.now().minus(1, ChronoUnit.MINUTES),
                null, null, null, null, null, pass.purgeAt()));
    }

    private static org.assertj.core.data.TemporalUnitOffset within10Minutes() {
        return new org.assertj.core.data.TemporalUnitWithinOffset(10, ChronoUnit.MINUTES);
    }
}

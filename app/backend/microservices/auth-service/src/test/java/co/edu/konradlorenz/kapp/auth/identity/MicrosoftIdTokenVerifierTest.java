package co.edu.konradlorenz.kapp.auth.identity;

import co.edu.konradlorenz.kapp.auth.config.MicrosoftProperties;
import co.edu.konradlorenz.kapp.auth.error.MicrosoftSignInUnavailableException;
import co.edu.konradlorenz.kapp.auth.identity.MicrosoftIdentity.DirectoryProfile;
import co.edu.konradlorenz.kapp.common.error.InvalidCredentialsException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The ID token check without a Spring context: a JDK HTTP server publishes the tenant's key set,
 * and each test signs the token it needs - including the ones that must be refused.
 */
class MicrosoftIdTokenVerifierTest {

    private static final String TENANT = "8c2b6e0a-5f1d-4b7e-9a3c-1d2e3f4a5b6c";
    private static final String ISSUER = "https://login.microsoftonline.com/" + TENANT + "/v2.0";
    private static final String EMULATOR_ISSUER = "http://10.0.2.2:8090/kapp";
    private static final String APP = "7f3e1a2b-0c4d-4e5f-8a9b-0c1d2e3f4a5b";
    private static final String PORTAL = "2a4c6e8f-1b3d-4f5a-9c7e-0b2d4f6a8c0e";
    private static final String OBJECT_ID = "0f1e2d3c-4b5a-4968-8776-655443322110";

    private static HttpServer keys;
    private static RSAKey tenantKey;
    private static String jwksUri;

    @BeforeAll
    static void publishTheTenantKeys() throws Exception {
        tenantKey = new RSAKeyGenerator(2048).keyID("tenant-key").generate();
        byte[] jwks = new JWKSet(tenantKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);

        keys = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        keys.createContext("/keys", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(jwks);
            }
        });
        keys.start();
        jwksUri = "http://127.0.0.1:" + keys.getAddress().getPort() + "/keys";
    }

    @AfterAll
    static void stop() {
        keys.stop(0);
    }

    // ---------------------------------------------------------------------------------
    // What a good token says
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("a token from the tenant, for KApp, unexpired: who the person is and their profile")
    void validToken_isRead() throws Exception {
        MicrosoftIdentity identity = verifier().verify(token(claims -> { }));

        assertThat(identity.objectId()).isEqualTo(OBJECT_ID);
        assertThat(identity.email()).isEqualTo("pepito.perez@konradlorenz.edu.co");
        assertThat(identity.givenName()).isEqualTo("Pepito");
        assertThat(identity.familyName()).isEqualTo("Perez Gomez");
        assertThat(identity.displayName()).isEqualTo("Pepito Perez Gomez");
        assertThat(identity.profile()).contains(DirectoryProfile.STUDENT);
    }

    @Test
    @DisplayName("without an email claim, the address is preferred_username")
    void email_fallsBackToPreferredUsername() throws Exception {
        MicrosoftIdentity identity = verifier().verify(token(claims -> claims
                .claim("email", null)
                .claim("preferred_username", "Laura.Gomez@konradlorenz.edu.co")));

        assertThat(identity.email()).isEqualTo("laura.gomez@konradlorenz.edu.co");
    }

    @Test
    @DisplayName("each profile value maps to its profile, ignoring case, and a student comes first")
    void profiles() throws Exception {
        assertThat(profileOf(List.of("Professor"))).contains(DirectoryProfile.PROFESSOR);
        assertThat(profileOf(List.of("Staff"))).contains(DirectoryProfile.STAFF);
        assertThat(profileOf(List.of("student"))).contains(DirectoryProfile.STUDENT);
        assertThat(profileOf(List.of("Professor", "Student"))).contains(DirectoryProfile.STUDENT);
    }

    /** An account the directory does not classify signs in as staff; that is decided by the caller. */
    @Test
    @DisplayName("no profile claim, or a value KApp does not know, gives no profile")
    void unknownProfile_isEmpty() throws Exception {
        assertThat(profileOf(List.of("Visitor"))).isEmpty();
        assertThat(verifier().verify(token(claims -> claims.claim("roles", null))).profile()).isEmpty();
    }

    @Test
    @DisplayName("the profile can come from another claim, holding one value rather than a list")
    void profile_fromAnotherClaim() throws Exception {
        MicrosoftIdTokenVerifier byJobTitle = new MicrosoftIdTokenVerifier(new MicrosoftProperties(null,
                List.of(ISSUER), jwksUri, List.of(APP), "jobTitle", List.of("Estudiante"), List.of("Docente"),
                List.of("Administrativo")));

        assertThat(byJobTitle.verify(token(claims -> claims.claim("jobTitle", "Docente"))).profile())
                .contains(DirectoryProfile.PROFESSOR);
    }

    @Test
    @DisplayName("any listed issuer and any of KApp's application ids are accepted")
    void otherListedIssuerAndAudience_areAccepted() throws Exception {
        MicrosoftIdentity identity = verifier().verify(token(claims -> claims
                .issuer(EMULATOR_ISSUER)
                .audience(List.of("some-other-api", PORTAL))));

        assertThat(identity.objectId()).isEqualTo(OBJECT_ID);
    }

    // ---------------------------------------------------------------------------------
    // What must be refused, each with the same 401
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("another directory's token is refused")
    void wrongIssuer_isRefused() throws Exception {
        assertRefused(token(claims -> claims.issuer("https://login.microsoftonline.com/common/v2.0")));
    }

    @Test
    @DisplayName("a token Microsoft issued to another application is refused")
    void wrongAudience_isRefused() throws Exception {
        assertRefused(token(claims -> claims.audience("some-other-application")));
    }

    @Test
    @DisplayName("an expired token is refused, past the minute of clock skew")
    void expired_isRefused() throws Exception {
        assertRefused(token(claims -> claims.expirationTime(Date.from(Instant.now().minusSeconds(120)))));
    }

    @Test
    @DisplayName("a token not valid yet is refused")
    void notYetValid_isRefused() throws Exception {
        assertRefused(token(claims -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(600)))));
    }

    @Test
    @DisplayName("a token without an expiry is refused")
    void noExpiry_isRefused() throws Exception {
        assertRefused(token(claims -> claims.expirationTime(null)));
    }

    @Test
    @DisplayName("a token signed by another key under the tenant's key id is refused")
    void forgedSignature_isRefused() throws Exception {
        RSAKey impostor = new RSAKeyGenerator(2048).keyID("tenant-key").generate();
        assertRefused(signedWith(impostor, claims -> { }));
    }

    @Test
    @DisplayName("a token signed by a key the tenant does not publish is refused")
    void unknownKey_isRefused() throws Exception {
        RSAKey stranger = new RSAKeyGenerator(2048).keyID("not-the-tenants").generate();
        assertRefused(signedWith(stranger, claims -> { }));
    }

    @Test
    @DisplayName("an unsigned token is refused")
    void unsigned_isRefused() {
        assertRefused(new PlainJWT(claims(claims -> { }).build()).serialize());
    }

    /** The old trick: sign with HMAC, using the public key as the secret. Only RS256 is accepted. */
    @Test
    @DisplayName("a token signed with a shared secret is refused")
    void hmacSigned_isRefused() throws Exception {
        SignedJWT hmac = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("tenant-key").build(),
                claims(claims -> { }).build());
        hmac.sign(new MACSigner(tenantKey.toPublicJWK().toRSAPublicKey().getEncoded()));
        assertRefused(hmac.serialize());
    }

    @Test
    @DisplayName("something that is not a token is refused")
    void malformed_isRefused() {
        assertRefused("not-a-token-at-all");
    }

    @Test
    @DisplayName("a token that does not name the person is refused")
    void noObjectId_isRefused() throws Exception {
        assertRefused(token(claims -> claims.claim("oid", null)));
    }

    @Test
    @DisplayName("a token without any address is refused")
    void noAddress_isRefused() throws Exception {
        assertRefused(token(claims -> claims.claim("email", null).claim("preferred_username", null)));
    }

    // ---------------------------------------------------------------------------------
    // The server's side
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("with Microsoft not configured, a sign-in is unavailable rather than refused")
    void notConfigured_isUnavailable() throws Exception {
        MicrosoftIdTokenVerifier unconfigured = new MicrosoftIdTokenVerifier(
                new MicrosoftProperties(null, null, null, null, null, null, null, null));
        String token = token(claims -> { });

        assertThatThrownBy(() -> unconfigured.verify(token)).isInstanceOf(MicrosoftSignInUnavailableException.class);
    }

    @Test
    @DisplayName("with the tenant's keys unreachable, a sign-in is unavailable rather than refused")
    void keysUnreachable_isUnavailable() throws Exception {
        MicrosoftIdTokenVerifier cutOff = new MicrosoftIdTokenVerifier(new MicrosoftProperties(null,
                List.of(ISSUER), "http://127.0.0.1:1/keys", List.of(APP), null, null, null, null));
        String token = token(claims -> { });

        assertThatThrownBy(() -> cutOff.verify(token)).isInstanceOf(MicrosoftSignInUnavailableException.class);
    }

    @Test
    @DisplayName("the tenant id alone gives Microsoft's issuer and key set for that tenant")
    void tenantId_derivesIssuerAndKeys() {
        MicrosoftProperties properties = new MicrosoftProperties(TENANT, null, null, List.of(APP), null, null, null,
                null);

        assertThat(properties.issuers()).containsExactly(ISSUER);
        assertThat(properties.jwksUri())
                .isEqualTo("https://login.microsoftonline.com/" + TENANT + "/discovery/v2.0/keys");
        assertThat(properties.profileClaim()).isEqualTo("roles");
        assertThat(properties.configured()).isTrue();
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private static MicrosoftIdTokenVerifier verifier() {
        return new MicrosoftIdTokenVerifier(new MicrosoftProperties(null, List.of(ISSUER, EMULATOR_ISSUER), jwksUri,
                List.of(APP, PORTAL), null, null, null, null));
    }

    private static java.util.Optional<DirectoryProfile> profileOf(List<String> roles) throws Exception {
        return verifier().verify(token(claims -> claims.claim("roles", roles))).profile();
    }

    private static void assertRefused(String token) {
        assertThatThrownBy(() -> verifier().verify(token))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(MicrosoftIdTokenVerifier.NOT_VERIFIED);
    }

    private static String token(Consumer<JWTClaimsSet.Builder> customise) throws Exception {
        return signedWith(tenantKey, customise);
    }

    private static String signedWith(RSAKey key, Consumer<JWTClaimsSet.Builder> customise) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
                claims(customise).build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    /** What Microsoft puts in an ID token for an invented student, before a test changes it. */
    private static JWTClaimsSet.Builder claims(Consumer<JWTClaimsSet.Builder> customise) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(APP)
                .subject("AAAAAAAAAAAAAAAAAAAAAIkzqFVrSaSaFHy782bbtaQ")
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now.minusSeconds(60)))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("oid", OBJECT_ID)
                .claim("tid", TENANT)
                .claim("email", "Pepito.Perez@konradlorenz.edu.co")
                .claim("preferred_username", "pepito.perez@konradlorenz.edu.co")
                .claim("given_name", "Pepito")
                .claim("family_name", "Perez Gomez")
                .claim("name", "Pepito Perez Gomez")
                .claim("roles", List.of("Student"))
                .claim("ver", "2.0");
        customise.accept(claims);
        return claims;
    }
}

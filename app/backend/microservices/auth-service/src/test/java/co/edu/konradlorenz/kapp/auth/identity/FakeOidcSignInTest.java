package co.edu.konradlorenz.kapp.auth.identity;

import co.edu.konradlorenz.kapp.auth.config.MicrosoftProperties;
import co.edu.konradlorenz.kapp.auth.identity.MicrosoftIdentity.DirectoryProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fake OIDC server of local development, with the very {@code fake-oidc/config.json} that
 * {@code docker-compose.yml} mounts, signed into the way a client does it - authorization code
 * with PKCE - and its ID token checked the way a Microsoft one is. If the fake directory and the
 * check ever disagree, development stops being able to sign in, and this says so first.
 */
@Testcontainers
class FakeOidcSignInTest {

    private static final String CLIENT = "kapp-app-dev";
    private static final String REDIRECT = "http://localhost/callback";
    /** RFC 7636's own example pair. */
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Container
    static final GenericContainer<?> FAKE_OIDC = new GenericContainer<>("ghcr.io/navikt/mock-oauth2-server:6.0.4")
            .withExposedPorts(8080)
            .withEnv("JSON_CONFIG_PATH", "/config/config.json")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("../fake-oidc/config.json")), "/config/config.json")
            .waitingFor(Wait.forHttp("/kapp/.well-known/openid-configuration").forStatusCode(200));

    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private static String issuer;
    private static MicrosoftIdTokenVerifier verifier;

    @BeforeAll
    static void pointTheCheckAtTheFakeServer() {
        issuer = "http://localhost:" + FAKE_OIDC.getMappedPort(8080) + "/kapp";
        verifier = new MicrosoftIdTokenVerifier(new MicrosoftProperties(null, List.of(issuer), issuer + "/jwks",
                List.of(CLIENT, "kapp-portal-dev"), null, null, null, null));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "estudiante,     estudiante@kforge.dev,     STUDENT",
            "profesor,       profesor@kforge.dev,       PROFESSOR",
            "administrativo, administrativo@kforge.dev, STAFF",
            "visitante,      visitante@kforge.dev,      ''"
    })
    @DisplayName("each invented person of the fake directory signs in, with their profile")
    void signsIn(String username, String email, String profile) throws Exception {
        MicrosoftIdentity identity = verifier.verify(idTokenFor(username));

        assertThat(identity.email()).isEqualTo(email);
        assertThat(identity.objectId()).isNotBlank();
        if (profile.isEmpty()) {
            assertThat(identity.profile()).isEmpty();
        } else {
            assertThat(identity.profile()).contains(DirectoryProfile.valueOf(profile));
        }
    }

    /** The login page's form, the redirect with the code, and the code exchanged for tokens. */
    private static String idTokenFor(String username) throws Exception {
        String authorize = issuer + "/authorize?" + form(Map.of(
                "client_id", CLIENT, "response_type", "code", "redirect_uri", REDIRECT,
                "scope", "openid profile email", "state", "s", "nonce", "n",
                "code_challenge", CHALLENGE, "code_challenge_method", "S256"));
        HttpResponse<String> login = HTTP.send(HttpRequest.newBuilder(URI.create(authorize))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(Map.of("username", username, "claims", ""))))
                .build(), HttpResponse.BodyHandlers.ofString());
        String location = login.headers().firstValue("Location").orElseThrow();
        String code = query(URI.create(location).getRawQuery()).get("code");

        HttpResponse<String> token = HTTP.send(HttpRequest.newBuilder(URI.create(issuer + "/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(Map.of(
                        "grant_type", "authorization_code", "code", code, "client_id", CLIENT,
                        "redirect_uri", REDIRECT, "code_verifier", VERIFIER))))
                .build(), HttpResponse.BodyHandlers.ofString());
        return new ObjectMapper().readTree(token.body()).get("id_token").asText();
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static Map<String, String> query(String raw) {
        return java.util.Arrays.stream(raw.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> java.net.URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
    }
}

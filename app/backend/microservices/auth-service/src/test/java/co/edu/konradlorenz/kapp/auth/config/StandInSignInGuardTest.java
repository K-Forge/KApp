package co.edu.konradlorenz.kapp.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link StandInSignInGuard}: the fake OIDC server and Atlas never meet. */
class StandInSignInGuardTest {

    private static final String ATLAS = "mongodb+srv://kapp_auth:secret@cluster0.example.mongodb.net/kapp_auth";
    private static final String LOCAL = "mongodb://kapp_auth:secret@mongo:27017/kapp_auth?replicaSet=rs0";
    private static final String TENANT = "00000000-0000-4000-8000-000000000000";

    private static MicrosoftProperties fakeOidc() {
        return new MicrosoftProperties(null,
                List.of("http://localhost:8090/kapp", "http://10.0.2.2:8090/kapp"),
                "http://fake-oidc:8080/kapp/jwks", List.of("kapp-app-dev"), null, null, null, null);
    }

    private static MicrosoftProperties tenant() {
        return new MicrosoftProperties(TENANT, null, null, List.of("an-app-id"), null, null, null, null);
    }

    @Test
    @DisplayName("the fake OIDC server against Atlas stops the service")
    void fakeOidcOnAtlas_refusesToStart() {
        assertThatThrownBy(() -> StandInSignInGuard.check(fakeOidc(), ATLAS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("http://fake-oidc:8080/kapp/jwks")
                .hasMessageNotContaining("secret");
    }

    @Test
    @DisplayName("an Atlas address without +srv is still Atlas")
    void plainAtlasAddress_isAtlas() {
        assertThat(StandInSignInGuard.isAtlas(
                "mongodb://u:p@ac-1-shard-00-00.example.mongodb.net:27017,ac-1-shard-00-01.example.mongodb.net:27017/kapp_auth"))
                .isTrue();
    }

    @Test
    @DisplayName("the fake OIDC server with the local-db database, the test tenant on Atlas, and nothing configured all start")
    void allowedCombinations_start() {
        assertThatCode(() -> StandInSignInGuard.check(fakeOidc(), LOCAL)).doesNotThrowAnyException();
        assertThatCode(() -> StandInSignInGuard.check(tenant(), ATLAS)).doesNotThrowAnyException();
        assertThatCode(() -> StandInSignInGuard.check(
                new MicrosoftProperties(null, null, null, null, null, null, null, null), ATLAS))
                .doesNotThrowAnyException();
        assertThatCode(() -> StandInSignInGuard.check(fakeOidc(), "")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an issuer that only looks like Microsoft's is not Microsoft's")
    void lookalike_isNotMicrosoft() {
        assertThat(StandInSignInGuard.isMicrosoft("https://login.microsoftonline.com/" + TENANT + "/v2.0")).isTrue();
        assertThat(StandInSignInGuard.isMicrosoft("http://login.microsoftonline.com/" + TENANT + "/v2.0")).isFalse();
        assertThat(StandInSignInGuard.isMicrosoft("https://login.microsoftonline.com.example.dev/kapp")).isFalse();
    }
}

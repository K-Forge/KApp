package co.edu.konradlorenz.kapp.auth;

import co.edu.konradlorenz.kapp.auth.config.LocalSignInGate;
import co.edu.konradlorenz.kapp.auth.domain.Credential;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What production answers: local sign-in off, as it is unless a server turns it on.
 *
 * <p>The operations to close are read from {@code docs/api/auth.openapi.yaml}, every one marked
 * {@code x-development-only}, so the gate cannot drift from the contract: an operation added there
 * and forgotten here fails this test, and so does a public path the gate swallows by mistake.
 */
@TestPropertySource(properties = "kapp.auth.local-sign-in.enabled=false")
class LocalSignInGateIntegrationTest extends AbstractAuthIntegrationTest {

    private static final Path CONTRACT = Path.of("../../../../docs/api/auth.openapi.yaml");
    private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete");

    record Operation(String method, String path, boolean developmentOnly) {

        /** The path with every parameter given a value, as a client would call it. */
        String concretePath() {
            return path.replaceAll("\\{[^}]+}", "KL-TEST-7K2M");
        }

        @Override
        public String toString() {
            return method.toUpperCase() + " " + path;
        }
    }

    @SuppressWarnings("unchecked")
    static List<Operation> operations() throws IOException {
        Map<String, Object> contract;
        try (InputStream in = Files.newInputStream(CONTRACT)) {
            contract = new Yaml().load(in);
        }
        List<Operation> operations = new ArrayList<>();
        ((Map<String, Map<String, Object>>) contract.get("paths")).forEach((path, item) -> {
            for (String method : METHODS) {
                if (item.get(method) instanceof Map<?, ?> operation) {
                    operations.add(new Operation(method, path,
                            Boolean.TRUE.equals(operation.get("x-development-only"))));
                }
            }
        });
        return operations;
    }

    static List<Operation> developmentOnly() throws IOException {
        return operations().stream().filter(Operation::developmentOnly).toList();
    }

    @Test
    @DisplayName("the contract still marks operations development-only, and the gate knows each of their paths")
    void gateMatchesTheContract() throws IOException {
        Set<String> contractPaths = developmentOnly().stream()
                .map(op -> op.path().replaceAll("\\{[^}]+}", "*"))
                .collect(Collectors.toSet());

        assertThat(contractPaths).isNotEmpty();
        assertThat(LocalSignInGate.DEVELOPMENT_ONLY_PATHS).containsExactlyInAnyOrderElementsOf(contractPaths);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("developmentOnly")
    @DisplayName("every development-only operation answers 404, even to an administrator")
    void developmentOnly_isNotFound(Operation operation) throws Exception {
        String admin = bearerFor("a0000000-0000-0000-0000-000000000001", "admin" + INSTITUTIONAL_DOMAIN, "ROLE_ADMIN");

        mockMvc.perform(request(HttpMethod.valueOf(operation.method().toUpperCase()), operation.concretePath())
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No endpoint matches this path"));
        mockMvc.perform(request(HttpMethod.valueOf(operation.method().toUpperCase()), operation.concretePath())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operations")
    @DisplayName("no operation the contract keeps in production is closed by the gate")
    void productionOperations_areNotGated(Operation operation) {
        assertThat(LocalSignInGate.isDevelopmentOnly(operation.concretePath()))
                .as(operation + (operation.developmentOnly() ? " is development-only" : " stays open"))
                .isEqualTo(operation.developmentOnly());
    }

    /** A password that is right still opens nothing: the path itself is gone. */
    @Test
    @DisplayName("a valid password signs nobody in")
    void validPassword_isNotFound() throws Exception {
        seedCredential("pepito.perez" + INSTITUTIONAL_DOMAIN, "ExamplePassword123", List.of("ROLE_STUDENT"),
                Credential.Status.ACTIVE, true);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "pepito.perez@konradlorenz.edu.co", "password": "ExamplePassword123"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an encoded letter does not slip past the gate")
    void encodedPath_isNotFound() throws Exception {
        // As sent, not re-encoded: a URI rather than a template.
        mockMvc.perform(post(URI.create("/auth/log%69n")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("what production keeps still answers: the JWKS document and the health probe")
    void productionPaths_stillAnswer() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk());
        mockMvc.perform(get("/auth/health")).andExpect(status().isOk());
    }
}

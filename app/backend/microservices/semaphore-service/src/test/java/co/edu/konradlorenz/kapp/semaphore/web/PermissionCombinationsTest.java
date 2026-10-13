package co.edu.konradlorenz.kapp.semaphore.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Tokens as auth 1.0 signs them: a profile role and permissions together. {@link AuthorizationMatrixTest}
 * holds every route against every single role; this holds one route of each kind of access against
 * the combinations, and what the contract's {@code x-roles} says each one may reach.
 *
 * <p>The rule it proves: a permission adds what it names and nothing else, and never takes away what
 * the profile role reaches. Reception, moderation, maintenance and Bienestar open nothing here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PermissionCombinationsTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    private static final Path CONTRACT = Path.of("../../../../docs/api/semaphore.openapi.yaml");

    static final List<List<String>> COMBINATIONS = List.of(
            List.of("ROLE_STUDENT", "ROLE_ADMIN"),
            List.of("ROLE_PROFESSOR", "ROLE_MODERATION"),
            List.of("ROLE_STAFF", "ROLE_RECEPTION"),
            List.of("ROLE_STAFF", "ROLE_MAINTENANCE", "ROLE_WELLBEING"),
            List.of("ROLE_STAFF", "ROLE_ADMIN"));

    /** One route of each kind of access, by the path the contract writes. */
    static final Map<String, Function<String, MockHttpServletRequestBuilder>> ROUTES = Map.of(
            "GET /api/catalog/programs", subject -> get("/api/catalog/programs"),
            "POST /api/catalog/pensums/import", subject -> multipart("/api/catalog/pensums/import")
                    .file(new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0])).param("dryRun", "true"),
            "GET /api/semaphore/me", subject -> get("/api/semaphore/me"));

    @Autowired
    private MockMvc mockMvc;

    static Stream<Arguments> cases() {
        return ROUTES.keySet().stream().sorted()
                .flatMap(route -> COMBINATIONS.stream().map(roles -> Arguments.of(route, roles)));
    }

    @ParameterizedTest(name = "{0} as {1}")
    @MethodSource("cases")
    void reachesWhatTheContractSays(String route, List<String> roles) throws Exception {
        boolean allowed = roles.stream().anyMatch(xRoles(route)::contains);
        String subject = "combo-" + String.join("-", roles).toLowerCase();

        int status = mockMvc.perform(ROUTES.get(route).apply(subject).with(caller(subject, roles)))
                .andReturn().getResponse().getStatus();

        if (allowed) {
            assertThat(status).as("%s as %s is let through", route, roles).isNotIn(401, 403);
        } else {
            assertThat(status).as("%s as %s is refused", route, roles).isEqualTo(403);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> xRoles(String route) throws Exception {
        String[] methodAndPath = route.split(" ", 2);
        Map<String, Object> contract;
        try (InputStream in = Files.newInputStream(CONTRACT)) {
            contract = new Yaml().load(in);
        }
        Map<String, Object> item = ((Map<String, Map<String, Object>>) contract.get("paths")).get(methodAndPath[1]);
        assertThat(item).as("the contract has %s", methodAndPath[1]).isNotNull();
        Map<String, Object> operation = (Map<String, Object>) item.get(methodAndPath[0].toLowerCase());
        return (List<String>) operation.get("x-roles");
    }

    private static RequestPostProcessor caller(String subject, List<String> roles) {
        return jwt().jwt(b -> b.subject(subject)
                        .claim("email", subject + "@konradlorenz.edu.co")
                        .claim("roles", roles))
                .authorities(roles.stream().map(SimpleGrantedAuthority::new).toArray(GrantedAuthority[]::new));
    }
}

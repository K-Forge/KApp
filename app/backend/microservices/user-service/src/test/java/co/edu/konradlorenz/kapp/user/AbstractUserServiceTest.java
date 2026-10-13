package co.edu.konradlorenz.kapp.user;

import co.edu.konradlorenz.kapp.user.domain.StoredInstant;
import co.edu.konradlorenz.kapp.user.domain.UserProfile;
import co.edu.konradlorenz.kapp.user.domain.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import co.edu.konradlorenz.kapp.user.client.CredentialStatusClient;
import co.edu.konradlorenz.kapp.user.sinu.SinuStudentPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Shared fixture for the profile tests: one MongoDB, one application context, and the
 * seed helpers every suite needs.
 *
 * <p>The container is a singleton started once for the whole run rather than a
 * {@code @Container} field per class. Five suites would otherwise pull up five replica
 * sets and five application contexts; sharing one of each keeps the annotations identical
 * across the subclasses, which is what lets Spring's test context cache reuse the context
 * too.
 *
 * <p>Between tests the documents are removed but the collection is <em>not</em> dropped.
 * Dropping it would take the Mongock indexes with it, and the search test's whole point
 * is that an index is there and is used - it would then pass or fail depending on the
 * order the suites ran in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "kapp.internal.token=" + AbstractUserServiceTest.INTERNAL_TOKEN
})
public abstract class AbstractUserServiceTest {

    /** The secret auth-service would hold. Any value works; both sides must agree. */
    public static final String INTERNAL_TOKEN = "test-internal-token-8f2c1d";

    /**
     * Stands in for auth-service. Deactivating an account suspends the credential over there,
     * and there is no auth-service in these tests - but the call must still be made, so this is
     * a stub rather than a switch that turns the behaviour off. {@link UserAdminControllerTest}
     * asserts against it.
     */
    @MockitoBean
    protected CredentialStatusClient credentialStatus;

    /**
     * The test SINU, wrapped rather than replaced: it answers as the {@code fake} adapter does -
     * the invented student of {@code docs/api/sinu/} - unless a test makes it fail. A spy in the
     * base class keeps one application context for every suite, where a mock in one suite would
     * start a second.
     */
    @MockitoSpyBean
    protected SinuStudentPort sinu;

    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    static {
        // Started here rather than by the Testcontainers extension so that a single
        // instance serves every subclass. Ryuk stops it when the JVM exits.
        MONGO.start();
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeEach
    void clearProfiles() {
        mongoTemplate.remove(new Query(), UserProfile.class);
    }

    // ---------------------------------------------------------------------------------
    // Callers
    // ---------------------------------------------------------------------------------

    /**
     * A validated token for one account, shaped exactly like the ones auth-service signs:
     * the id in {@code sub}, the roles already prefixed in the {@code roles} claim.
     */
    protected static RequestPostProcessor callerWith(String userId, UserRole... roles) {
        List<String> names = Arrays.stream(roles).map(UserRole::name).toList();
        return jwt()
                .jwt(builder -> builder
                        .subject(userId)
                        .claim("email", userId + "@konradlorenz.edu.co")
                        .claim("roles", names))
                .authorities(names.stream().map(SimpleGrantedAuthority::new).toArray(GrantedAuthority[]::new));
    }

    // ---------------------------------------------------------------------------------
    // Seed data
    // ---------------------------------------------------------------------------------

    protected UserProfile save(UserProfile profile) {
        return mongoTemplate.save(profile);
    }

    /** A student. What they study is SINU's, so the profile holds none of it. */
    protected static UserProfile student(String id, String email, String firstName,
                                         String lastName) {
        return profile(id, email, firstName, lastName, UserRole.ROLE_STUDENT);
    }

    protected static UserProfile professor(String id, String email, String firstName,
                                           String lastName) {
        return profile(id, email, firstName, lastName, UserRole.ROLE_PROFESSOR);
    }

    /** An administrative employee who also administers KApp, as the contract's example. */
    protected static UserProfile admin(String id, String email, String firstName,
                                       String lastName) {
        return profile(id, email, firstName, lastName, UserRole.ROLE_STAFF, UserRole.ROLE_ADMIN);
    }

    protected static UserProfile profile(String id, String email, String firstName,
                                         String lastName, UserRole... roles) {
        Instant now = StoredInstant.now();
        return new UserProfile(id, email, firstName, lastName, null, List.of(roles), true,
                List.of(), now, now);
    }

    /** A document with every optional field populated, for round-trip assertions. */
    protected static UserProfile fullyPopulated(String id, String email) {
        Instant now = StoredInstant.now();
        return new UserProfile(id, email, "Pepito", "Perez Gomez",
                "https://cdn.kapp.konradlorenz.edu.co/avatars/3f8a1c2e.jpg",
                List.of(UserRole.ROLE_STUDENT), true, List.of(), now, now);
    }

    /** Ages a profile so that "newest first" has something to order by. */
    protected static UserProfile createdMinutesAgo(UserProfile profile, int minutes) {
        Instant when = StoredInstant.of(profile.createdAt().minus(minutes, ChronoUnit.MINUTES));
        return new UserProfile(profile.id(), profile.email(), profile.firstName(),
                profile.lastName(), profile.avatarUrl(), profile.roles(), profile.active(),
                List.of(), when, when);
    }

    protected UserProfile reload(String id) {
        return mongoTemplate.findById(id, UserProfile.class);
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}

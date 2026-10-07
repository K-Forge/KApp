package co.edu.konradlorenz.kapp.auth;

import co.edu.konradlorenz.kapp.auth.client.InternalUserUpsert;
import co.edu.konradlorenz.kapp.auth.domain.Credential;
import co.edu.konradlorenz.kapp.auth.service.InvitationCodeService;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration: institutional and guest signup, invitation codes, and the ordering and
 * failure modes {@code docs/INTEGRATION-NOTES.md} section 2 calls out.
 *
 * <p>Institutional tests reuse the Mongock-seeded {@code KL-20262-STUDENT} /
 * {@code KL-20262-STAFF} codes, which carry generous quotas (200 / 20) meant to survive
 * a whole test suite. Anything that needs a specific quota - exhausted, expired, a rogue
 * role - seeds its own {@code KL-TEST-*} code instead, cleaned up per test by the shared
 * fixture.
 */
class RegistrationIntegrationTest extends AbstractAuthIntegrationTest {

    private static final String STUDENT_CODE = "KL-20262-STUDENT";
    private static final String STAFF_CODE = "KL-20262-STAFF";

    @Autowired
    private InvitationCodeService invitationCodeService;

    @Test
    @DisplayName("registers an institutional student and returns emailVerified=false")
    void institutionalRegistration_succeeds() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("pepito.perez@konradlorenz.edu.co",
                                STUDENT_CODE, "506999999", "506")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("pepito.perez@konradlorenz.edu.co"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.userId").isNotEmpty());

        Credential saved = credentials.findByEmailIgnoreCase("pepito.perez@konradlorenz.edu.co")
                .orElseThrow();
        assertThat(saved.roles()).containsExactly("ROLE_STUDENT");
        // Off by default: no SMTP relay, so the account must not be blocked on it.
        assertThat(saved.status()).isEqualTo(Credential.Status.ACTIVE);
        assertThat(saved.emailVerified()).isFalse();
    }

    @Test
    @DisplayName("lower-cases the e-mail before storing and checking for a duplicate")
    void institutionalRegistration_lowerCasesEmail() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("Pepito.Perez@KonradLorenz.edu.co",
                                STUDENT_CODE, "506232731", "506")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("pepito.perez@konradlorenz.edu.co"));

        assertThat(credentials.existsByEmailIgnoreCase("pepito.perez@konradlorenz.edu.co")).isTrue();
    }

    @Test
    @DisplayName("creates the profile in user-service with the fields registration collected")
    void institutionalRegistration_sendsExpectedProfileToUserService() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("ana.gomez@konradlorenz.edu.co",
                                STUDENT_CODE, "506111222", "506")))
                .andExpect(status().isCreated());

        var captor = org.mockito.ArgumentCaptor.forClass(InternalUserUpsert.class);
        verify(userProfileClient).upsert(captor.capture());
        InternalUserUpsert sent = captor.getValue();
        assertThat(sent.email()).isEqualTo("ana.gomez@konradlorenz.edu.co");
        // Since user 1.0 the profile carries the roles and nothing academic: user-service keeps
        // no student code, and reads a student's program, pensum and level from SINU.
        assertThat(sent.roles()).containsExactly("ROLE_STUDENT");
        assertThat(sent.firstName()).isNotBlank();
    }

    @Test
    @DisplayName("rejects a non-institutional e-mail on the institutional endpoint")
    void institutionalRegistration_rejectsOutsideDomain() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("someone@gmail.com",
                                STUDENT_CODE, "506000111", "506")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("email"));

        assertThat(credentials.existsByEmailIgnoreCase("someone@gmail.com")).isFalse();
    }

    /**
     * The allowed domain is a LIST, not one value, and that is the whole point: the team's own
     * accounts live on kforge.dev while a student registers with their institutional address.
     * Without this test the list could collapse back to a single domain and only the four
     * development accounts would notice - by silently failing to be creatable.
     */
    @Test
    @DisplayName("a second configured domain is accepted")
    void institutionalRegistration_acceptsSecondaryDomain() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("dev.account@kforge.dev",
                                STUDENT_CODE, "506000222", "506")))
                .andExpect(status().isCreated());

        assertThat(credentials.existsByEmailIgnoreCase("dev.account@kforge.dev")).isTrue();
    }

    @Test
    @DisplayName("a domain that is not configured is still refused, and the message names both")
    void institutionalRegistration_rejectsUnconfiguredDomain() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("someone@kforge.com",
                                STUDENT_CODE, "506000333", "506")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("email"))
                .andExpect(jsonPath("$.details[0].issue")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("@konradlorenz.edu.co"),
                                org.hamcrest.Matchers.containsString("@kforge.dev"))));

        assertThat(credentials.existsByEmailIgnoreCase("someone@kforge.com")).isFalse();
    }

    @Test
    @DisplayName("requires studentCode and programCode for a student invitation code")
    void institutionalRegistration_studentCodeRequiresAcademicFields() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("no.academic@konradlorenz.edu.co",
                                STUDENT_CODE, null, null)))
                .andExpect(status().isBadRequest());

        assertThat(credentials.existsByEmailIgnoreCase("no.academic@konradlorenz.edu.co")).isFalse();
    }

    @Test
    @DisplayName("a staff invitation code grants ROLE_PROFESSOR without an academic record")
    void institutionalRegistration_staffCodeGrantsProfessorWithNoAcademic() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("prof.lopez@konradlorenz.edu.co",
                                STAFF_CODE, null, null)))
                .andExpect(status().isCreated());

        assertThat(credentials.findByEmailIgnoreCase("prof.lopez@konradlorenz.edu.co").orElseThrow().roles())
                .containsExactly("ROLE_PROFESSOR");

        var captor = org.mockito.ArgumentCaptor.forClass(InternalUserUpsert.class);
        verify(userProfileClient).upsert(captor.capture());
        assertThat(captor.getValue().roles()).containsExactly("ROLE_PROFESSOR");
    }

    @Test
    @DisplayName("a duplicate e-mail is refused with 409")
    void institutionalRegistration_duplicateEmailReturns409() throws Exception {
        String payload = institutionalPayload("duplicate@konradlorenz.edu.co",
                STUDENT_CODE, "506999888", "506");

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("an unknown invitation code is refused with 400")
    void institutionalRegistration_unknownCodeReturns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("someone@konradlorenz.edu.co",
                                "KL-DOES-NOT-EXIST", "506123123", "506")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("invitationCode"));
    }

    @Test
    @DisplayName("an already-spent invitation code is refused with 400")
    void institutionalRegistration_exhaustedCodeReturns400() throws Exception {
        seedInvitationCode("KL-TEST-EXHAUSTED", "ROLE_STUDENT", 1, 1, true, null);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("late@konradlorenz.edu.co",
                                "KL-TEST-EXHAUSTED", "506123999", "506")))
                .andExpect(status().isBadRequest());

        assertThat(credentials.existsByEmailIgnoreCase("late@konradlorenz.edu.co")).isFalse();
    }

    @Test
    @DisplayName("an expired invitation code is refused with 400")
    void institutionalRegistration_expiredCodeReturns400() throws Exception {
        seedInvitationCode("KL-TEST-EXPIRED", "ROLE_STUDENT", 50, 0, true,
                Instant.now().minusSeconds(3600));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("toolate@konradlorenz.edu.co",
                                "KL-TEST-EXPIRED", "506123998", "506")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a code carrying ROLE_ADMIN never grants admin through signup")
    void institutionalRegistration_adminRoleCodeIsRejected() throws Exception {
        seedInvitationCode("KL-TEST-ADMIN", "ROLE_ADMIN", 5, 0, true, null);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("wannabe@konradlorenz.edu.co",
                                "KL-TEST-ADMIN", "506123997", "506")))
                .andExpect(status().isBadRequest());

        assertThat(credentials.existsByEmailIgnoreCase("wannabe@konradlorenz.edu.co")).isFalse();
        // The failed attempt must not have permanently cost the registrar the slot.
        assertThat(invitationCodeService.find("KL-TEST-ADMIN").timesUsed()).isZero();
    }

    @Test
    @DisplayName("a failed registration releases the invitation code it claimed")
    void institutionalRegistration_profileServiceDown_releasesCodeAndReturns503() throws Exception {
        seedInvitationCode("KL-TEST-RELEASE", "ROLE_STUDENT", 1, 0, true, null);
        when(userProfileClient.upsert(any())).thenThrow(new RuntimeException("user-service unreachable"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(institutionalPayload("orphan@konradlorenz.edu.co",
                                "KL-TEST-RELEASE", "506123996", "506")))
                .andExpect(status().isServiceUnavailable());

        assertThat(credentials.existsByEmailIgnoreCase("orphan@konradlorenz.edu.co")).isFalse();
        // Given back: a crashed registration must not cost the registrar an intake slot.
        assertThat(invitationCodeService.find("KL-TEST-RELEASE").timesUsed()).isZero();
    }

    @Test
    @DisplayName("ten threads racing a single-use code produce exactly one success")
    void invitationCodeRedemption_isAtomicUnderConcurrency() throws Exception {
        String code = "KL-TEST-CONCURRENCY";
        seedInvitationCode(code, "ROLE_STUDENT", 1, 0, true, null);

        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger refusals = new AtomicInteger();

        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        invitationCodeService.redeem(code);
                        successes.incrementAndGet();
                    } catch (BusinessRuleException e) {
                        refusals.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            ready.await();
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(refusals.get()).isEqualTo(threadCount - 1);
        assertThat(invitationCodeService.find(code).timesUsed()).isEqualTo(1);
    }

    private static String institutionalPayload(String email, String invitationCode,
                                               String studentCode, String programCode) {
        return """
                {
                    "email": "%s",
                    "password": "ExamplePassword123",
                    "firstName": "Test",
                    "lastName": "User",
                    "invitationCode": "%s"%s
                }""".formatted(email, invitationCode, academicFields(studentCode, programCode));
    }

    private static String academicFields(String studentCode, String programCode) {
        if (studentCode == null && programCode == null) {
            return "";
        }
        return """
                ,
                    "studentCode": %s,
                    "programCode": %s""".formatted(
                studentCode == null ? "null" : "\"" + studentCode + "\"",
                programCode == null ? "null" : "\"" + programCode + "\"");
    }

    private static String guestPayload(String email) {
        return """
                {
                    "email": "%s",
                    "password": "VisitanteSegura!26",
                    "firstName": "Guest",
                    "lastName": "Person"
                }""".formatted(email);
    }
}

package co.edu.konradlorenz.kapp.semaphore.web;

import co.edu.konradlorenz.kapp.semaphore.repository.PensumRepository;
import co.edu.konradlorenz.kapp.semaphore.repository.ProgramRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bulk import of pensums: {@code POST /api/catalog/pensums/import}.
 *
 * <p>Two behaviours carry the weight. **Nothing is written unless everything validates** — a
 * partial import would leave the catalogue in a state nobody chose. And **declared totals are
 * checked rather than trusted**, which is the case the reconstructed Ingeniería de Sistemas seed
 * had: it declared 142 credits where its courses added up to 144.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class PensumImportFlowTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PensumRepository pensums;
    @Autowired
    private ProgramRepository programs;

    private static final String HEADER =
            "programCode,programName,faculty,programLevel,pensumCode,reform,pensumStatus,"
            + "declaredCredits,declaredHours,levels,areaCode,areaName,areaColor,"
            + "pensumItemCode,courseCode,courseName,courseLevel,credits,weeklyHours,"
            + "isElectiveSlot,prerequisites,sinuCode";

    @AfterEach
    void removeImported() {
        pensums.findAll().stream()
                .filter(c -> c.pensumCode().startsWith("IMP-"))
                .forEach(c -> pensums.deleteById(c.pensumCode()));
        programs.findAll().stream()
                .filter(p -> p.code().startsWith("IMP-"))
                .forEach(p -> programs.deleteById(p.code()));
    }

    // ── The happy path ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a valid file imports the program and the pensum together")
    void validFileImports() throws Exception {
        mockMvc.perform(upload(twoCourseFile("IMP-OK", 6, 8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.rowsRead").value(2))
                .andExpect(jsonPath("$.pensums[0].pensumCode").value("IMP-OK"))
                .andExpect(jsonPath("$.pensums[0].courses").value(2))
                .andExpect(jsonPath("$.pensums[0].pensumCreated").value(true));

        assertThat(pensums.findById("IMP-OK")).isPresent();
        assertThat(programs.findById("IMP-PROG")).isPresent();
    }

    @Test
    @DisplayName("the imported pensum is readable through the catalog straight away")
    void importedPensumIsReadable() throws Exception {
        mockMvc.perform(upload(twoCourseFile("IMP-READ", 6, 8))).andExpect(status().isOk());

        mockMvc.perform(get("/api/catalog/pensums/{code}", "IMP-READ").with(admin("reader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(2))
                .andExpect(jsonPath("$.areas.length()").value(1));
    }

    @Test
    @DisplayName("area credits and hours are summed from the courses, not transcribed")
    void areaTotalsAreDerived() throws Exception {
        mockMvc.perform(upload(twoCourseFile("IMP-AREAS", 6, 8))).andExpect(status().isOk());

        mockMvc.perform(get("/api/catalog/pensums/{code}", "IMP-AREAS").with(admin("areas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areas[0].credits").value(6))
                .andExpect(jsonPath("$.areas[0].hours").value(8));
    }

    @Test
    @DisplayName("one file may carry several pensums")
    void severalPensumsInOneFile() throws Exception {
        // Each pensum declares its own totals; the one-argument row() derives them from the
        // single course it carries.
        String csv = HEADER + "\n"
                + row("IMP-A", "CB", "A1", "A1C", "Uno", 1, 3, 4, false, "")
                + row("IMP-B", "CB", "B1", "B1C", "Dos", 1, 3, 4, false, "");

        mockMvc.perform(upload(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pensums.length()").value(2));
    }

    // ── Dry run ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a dry run reports what would happen and writes nothing")
    void dryRunWritesNothing() throws Exception {
        mockMvc.perform(multipart("/api/catalog/pensums/import")
                        .file(file(twoCourseFile("IMP-DRY", 6, 8)))
                        .param("dryRun", "true")
                        .with(admin("dry")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.pensums[0].pensumCode").value("IMP-DRY"));

        assertThat(pensums.findById("IMP-DRY")).isEmpty();
    }

    // ── Totals are checked, not trusted ────────────────────────────────────────────

    @Test
    @DisplayName("declared credits that disagree with the courses stop the import, naming both numbers")
    void wrongDeclaredCreditsIsReported() throws Exception {
        mockMvc.perform(upload(twoCourseFile("IMP-CREDITS", 99, 8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field=~/.*declaredCredits.*/)]").isNotEmpty())
                .andExpect(jsonPath("$.details[0].issue")
                        .value(org.hamcrest.Matchers.containsString("99")));

        assertThat(pensums.findById("IMP-CREDITS")).isEmpty();
    }

    @Test
    @DisplayName("declared hours are checked against the sum of WEEKLY hours")
    void wrongDeclaredHoursIsReported() throws Exception {
        mockMvc.perform(upload(twoCourseFile("IMP-HOURS", 6, 500)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field=~/.*declaredHours.*/)]").isNotEmpty());
    }

    // ── Per-row errors ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a bad row is reported against the line number of the file")
    void rowErrorsCarryTheLineNumber() throws Exception {
        String csv = HEADER + "\n"
                + row("IMP-BAD", "CB", "X1", "X1C", "Bien", 1, 3, 4, false, "")
                + "IMP-PROG,Programa,Facultad,PREGRADO,IMP-BAD,R,DRAFT,6,8,2,CB,Ciencias,"
                + "#539392,X2,X2C,Mal,NO-ES-NUMERO,3,4,false,,\n";

        mockMvc.perform(upload(csv))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field=~/row 3.*/)]").isNotEmpty());
    }

    @Test
    @DisplayName("a prerequisite that is not in the same pensum is refused")
    void danglingPrerequisiteIsRefused() throws Exception {
        String csv = HEADER + "\n"
                + row("IMP-PRE", "CB", "P1", "P1C", "Uno", 1, 3, 4, false, "NO-EXISTE");

        mockMvc.perform(upload(csv))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.issue=~/.*NO-EXISTE.*/)]").isNotEmpty());
    }

    @Test
    @DisplayName("a repeated pensumItemCode inside one pensum is refused")
    void duplicateItemCodeIsRefused() throws Exception {
        String csv = HEADER + "\n"
                + row("IMP-DUP", "CB", "D1", "D1C", "Uno", 1, 3, 4, false, "")
                + row("IMP-DUP", "CB", "D1", "D2C", "Otro", 1, 3, 4, false, "");

        mockMvc.perform(upload(csv))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.issue=~/.*twice.*/)]").isNotEmpty());
    }

    @Test
    @DisplayName("rows that disagree about the same pensum's header are refused")
    void inconsistentHeaderRowsAreRefused() throws Exception {
        String csv = HEADER + "\n"
                + row("IMP-INC", "CB", "I1", "I1C", "Uno", 1, 3, 4, false, "")
                + "IMP-OTRO,Programa,Facultad,PREGRADO,IMP-INC,R,DRAFT,6,8,2,CB,Ciencias,"
                + "#539392,I2,I2C,Dos,1,3,4,false,,\n";

        mockMvc.perform(upload(csv))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.issue=~/.*disagrees.*/)]").isNotEmpty());
    }

    @Test
    @DisplayName("an elective slot carrying a course code is refused")
    void electiveSlotWithACodeIsRefused() throws Exception {
        String csv = HEADER + "\n"
                + row("IMP-ELEC", "CB", "E1", "E1C", "Electiva", 1, 3, 4, true, "");

        mockMvc.perform(upload(csv))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("nothing at all is written when any row fails")
    void aFailedImportWritesNothing() throws Exception {
        String csv = HEADER + "\n"
                + row("IMP-ATOMIC", "CB", "A1", "A1C", "Bien", 1, 3, 4, false, "")
                + row("IMP-ATOMIC", "CB", "A2", "A2C", "Tambien bien", 1, 3, 4, false, "NO-EXISTE");

        mockMvc.perform(upload(csv)).andExpect(status().isBadRequest());

        assertThat(pensums.findById("IMP-ATOMIC")).isEmpty();
        assertThat(programs.findById("IMP-PROG")).isEmpty();
    }

    @Test
    @DisplayName("one missing header field is reported once, not as a fault on every other row")
    void aBrokenHeaderDoesNotCascade() throws Exception {
        // The empty sixth column is `reform`. readHeader throws there, before it reaches
        // declaredCredits and declaredHours - which then stay 0 and used to be reported as a
        // disagreement on every later row and as totals that do not add up: eleven messages
        // for one mistake, ten of them naming rows that were correct.
        String csv = HEADER + "\n"
                + "IMP-PROG,Programa,Facultad,PREGRADO,IMP-NOREF,,DRAFT,6,8,2,CB,Ciencias,"
                + "#539392,B1,B1C,Uno,1,3,4,false,,\n"
                + "IMP-PROG,Programa,Facultad,PREGRADO,IMP-NOREF,,DRAFT,6,8,2,CB,Ciencias,"
                + "#539392,B2,B2C,Dos,1,3,4,false,,\n";

        String body = mockMvc.perform(upload(csv))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].field").value("row 2 · pensum header"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("reform");
        assertThat(body).doesNotContain("disagrees");
        assertThat(body).doesNotContain("add up to");
    }

    @Test
    @DisplayName("a file with only a header is refused rather than reported as a success")
    void headerOnlyFileIsRefused() throws Exception {
        mockMvc.perform(upload(HEADER + "\n"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("what the import accepts, the update accepts: a pensum built here can be edited here")
    void importedPensumSurvivesBeingSavedBack() throws Exception {
        // COMPLEMENTARIA is fourteen characters. The import used to write it and the PUT used to
        // refuse it, so the portal produced pensums it could not then edit - and the message the
        // admin saw was "size must be between 0 and 10" about a document they never typed.
        String csv = HEADER + "\n"
                + "IMP-PROG,Programa,Facultad,PREGRADO,IMP-WORDS,R,DRAFT,3,4,1,COMPLEMENTARIA,"
                + "Complementaria,#539392,W1,W1C,Uno,1,3,4,false,,\n";

        mockMvc.perform(upload(csv)).andExpect(status().isOk());

        String document = mockMvc.perform(
                        get("/api/catalog/pensums/{code}", "IMP-WORDS").with(admin("editor")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        mockMvc.perform(put("/api/catalog/pensums/{code}", "IMP-WORDS")
                        .with(admin("editor"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(document))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the import refuses what the update would refuse, instead of writing it")
    void importAppliesTheSameFieldRules() throws Exception {
        String tooLong = "A".repeat(21);
        String csv = HEADER + "\n"
                + "IMP-PROG,Programa,Facultad,PREGRADO,IMP-LONG,R,DRAFT,3,4,1," + tooLong + ","
                + "Larga,#539392,L1,L1C,Uno,1,3,4,false,,\n";

        mockMvc.perform(upload(csv))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.issue=~/.*between 0 and 20.*/)]").isNotEmpty());

        assertThat(pensums.findById("IMP-LONG")).isEmpty();
    }

    @Test
    @DisplayName("accented course names survive the round trip")
    void utf8SurvivesTheImport() throws Exception {
        String csv = HEADER + "\n"
                + row("IMP-UTF8", "CB", "U1", "U1C", "Matemáticas Discretas", 1, 3, 4, false, "");

        mockMvc.perform(upload(csv)).andExpect(status().isOk());

        mockMvc.perform(get("/api/catalog/pensums/{code}", "IMP-UTF8").with(admin("utf8")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].name").value("Matemáticas Discretas"));
    }

    @Test
    @DisplayName("a quoted field containing a comma is one value, not two")
    void quotedCommaIsOneField() throws Exception {
        String csv = HEADER + "\n"
                + "IMP-PROG,Programa,Facultad,PREGRADO,IMP-QUOTE,R,DRAFT,3,4,2,CB,Ciencias,"
                + "#539392,Q1,Q1C,\"Etica, Ciudadania y Sociedad\",1,3,4,false,,\n";

        mockMvc.perform(upload(csv)).andExpect(status().isOk());

        mockMvc.perform(get("/api/catalog/pensums/{code}", "IMP-QUOTE").with(admin("quote")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].name").value("Etica, Ciudadania y Sociedad"));
    }

    // ── Authorization ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("importing without a token is 401")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/catalog/pensums/import")
                        .file(file(twoCourseFile("IMP-ANON", 6, 8))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a student cannot import pensums")
    void studentIsForbidden() throws Exception {
        mockMvc.perform(multipart("/api/catalog/pensums/import")
                        .file(file(twoCourseFile("IMP-STU", 6, 8)))
                        .with(jwtWithRole("s1", "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a professor cannot import pensums")
    void professorIsForbidden() throws Exception {
        mockMvc.perform(multipart("/api/catalog/pensums/import")
                        .file(file(twoCourseFile("IMP-PROF", 6, 8)))
                        .with(jwtWithRole("p1", "ROLE_PROFESSOR")))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.RequestBuilder upload(String csv) {
        return multipart("/api/catalog/pensums/import").file(file(csv)).with(admin("importer"));
    }

    private static MockMultipartFile file(String csv) {
        return new MockMultipartFile("file", "pensums.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
    }

    private static String twoCourseFile(String pensumCode, int declaredCredits, int declaredHours) {
        return HEADER + "\n"
                + row(pensumCode, "CB", "T1", "T1C", "Curso Uno", 1, 3, 4, false, "",
                      declaredCredits, declaredHours)
                + row(pensumCode, "CB", "T2", "T2C", "Curso Dos", 2, 3, 4, false, "T1C",
                      declaredCredits, declaredHours);
    }

    /** One CSV line, with the single-course totals a one-row pensum needs. */
    private static String row(String pensumCode, String area, String itemCode, String courseCode,
                               String name, int level, int credits, int weeklyHours,
                               boolean elective, String prerequisites) {
        return row(pensumCode, area, itemCode, courseCode, name, level, credits, weeklyHours,
                elective, prerequisites, credits, weeklyHours);
    }

    private static String row(String pensumCode, String area, String itemCode, String courseCode,
                               String name, int level, int credits, int weeklyHours,
                               boolean elective, String prerequisites,
                               int declaredCredits, int declaredHours) {
        return "IMP-PROG,Programa,Facultad,PREGRADO,%s,R,DRAFT,%d,%d,2,%s,Ciencias,#539392,%s,%s,%s,%d,%d,%d,%s,%s,\n"
                .formatted(pensumCode, declaredCredits, declaredHours, area, itemCode,
                        courseCode, name, level, credits, weeklyHours, elective, prerequisites);
    }

    private static RequestPostProcessor admin(String subject) {
        return jwtWithRole(subject, "ROLE_ADMIN");
    }

    private static RequestPostProcessor jwtWithRole(String subject, String role) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(b -> b.subject(subject)
                        .claim("email", subject + "@konradlorenz.edu.co")
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority(role));
    }
}

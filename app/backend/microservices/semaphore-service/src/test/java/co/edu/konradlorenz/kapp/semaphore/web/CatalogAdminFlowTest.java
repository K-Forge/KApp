package co.edu.konradlorenz.kapp.semaphore.web;

import co.edu.konradlorenz.kapp.semaphore.domain.PensumStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.ProgramLevel;
import co.edu.konradlorenz.kapp.semaphore.domain.StudentProgress;
import co.edu.konradlorenz.kapp.semaphore.repository.PensumRepository;
import co.edu.konradlorenz.kapp.semaphore.repository.ProgramRepository;
import co.edu.konradlorenz.kapp.semaphore.repository.StudentProgressRepository;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumAreaDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumCourseDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.ProgramRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administration of the catalog: creating, replacing and deleting programs, and deleting
 * pensums.
 *
 * <p>The rule these tests exist for is that <strong>deleting never cascades</strong>. A
 * program with pensums and a pensum with students both refuse with {@code 409}, and
 * they name what is blocking them - an administrator who is told only "conflict" has to go
 * and find out for themselves, which is the point at which people start deleting things by
 * hand in mongosh.
 *
 * <p>Everything created here is prefixed {@code T-} and removed afterwards, because the
 * Spring context and its Mongo container are shared across the suite and the seeded
 * program 506 / pensum 1015 are relied on by other classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CatalogAdminFlowTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private ProgramRepository programs;
    @Autowired
    private PensumRepository pensums;
    @Autowired
    private StudentProgressRepository progress;

    private static final String SEEDED_PROGRAM = "506";
    private static final String SEEDED_PENSUM = "1015";

    @AfterEach
    void removeWhatThisClassCreated() {
        programs.findAll().stream()
                .filter(p -> p.code().startsWith("T-"))
                .forEach(p -> programs.deleteById(p.code()));
        pensums.findAll().stream()
                .filter(c -> c.pensumCode().startsWith("T-"))
                .forEach(c -> pensums.deleteById(c.pensumCode()));
        progress.findAll().stream()
                .filter(p -> p.userId().startsWith("T-"))
                .forEach(p -> progress.deleteById(p.id()));
    }

    // ── Creating a program ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("an admin can create a program, and it appears in the catalog")
    void createProgramThenReadItBack() throws Exception {
        mockMvc.perform(post("/api/catalog/programs").with(admin("create-prog"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programJson("T-NEW", "Nuevo Programa", ProgramLevel.PREGRADO)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/catalog/programs/T-NEW"))
                .andExpect(jsonPath("$.code").value("T-NEW"))
                .andExpect(jsonPath("$.name").value("Nuevo Programa"));

        mockMvc.perform(get("/api/catalog/programs/{code}", "T-NEW").with(admin("read-prog")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("PREGRADO"));
    }

    @Test
    @DisplayName("a brand new program has no active pensum, and says so rather than omitting it")
    void newProgramHasNullActivePensum() throws Exception {
        mockMvc.perform(post("/api/catalog/programs").with(admin("null-pensum"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programJson("T-NOPLAN", "Sin Pensum", ProgramLevel.MAESTRIA)))
                .andExpect(status().isCreated())
                // Present and null, not absent: ProgramResponse carries no @JsonInclude, so
                // the client always sees the field and never has to guess whether a missing
                // key means "no active pensum" or "this server is older than the field".
                .andExpect(jsonPath("$.activePensumCode").value(nullValue()));
    }

    @Test
    @DisplayName("creating a program whose code is taken is rejected with 409")
    void duplicateProgramCodeIsConflict() throws Exception {
        mockMvc.perform(post("/api/catalog/programs").with(admin("dup-prog-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programJson("T-DUP", "Primero", ProgramLevel.PREGRADO)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/catalog/programs").with(admin("dup-prog-2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programJson("T-DUP", "Segundo", ProgramLevel.PREGRADO)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CURSOS_DIPLOMADOS is accepted: the request enum must cover every level a program can hold")
    void cursosDiplomadosIsAValidLevel() throws Exception {
        mockMvc.perform(post("/api/catalog/programs").with(admin("diplomado"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programJson("T-DIPL", "Diplomado", ProgramLevel.CURSOS_DIPLOMADOS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.level").value("CURSOS_DIPLOMADOS"));
    }

    // ── Replacing a program ────────────────────────────────────────────────────────

    @Test
    @DisplayName("replacing a program updates it in place")
    void replaceProgramUpdatesIt() throws Exception {
        mockMvc.perform(post("/api/catalog/programs").with(admin("repl-seed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programJson("T-REPL", "Nombre Viejo", ProgramLevel.PREGRADO)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/catalog/programs/{code}", "T-REPL").with(admin("repl"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programJson("T-REPL", "Nombre Nuevo", ProgramLevel.POSGRADO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nombre Nuevo"))
                .andExpect(jsonPath("$.level").value("POSGRADO"));
    }

    @Test
    @DisplayName("a body whose code disagrees with the path is rejected with 400, naming the field")
    void replaceWithMismatchedCodeIsRejected() throws Exception {
        mockMvc.perform(put("/api/catalog/programs/{code}", "T-PATH").with(admin("mismatch"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programJson("T-BODY", "Discrepante", ProgramLevel.PREGRADO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("code"));
    }

    @Test
    @DisplayName("replacing a program that does not exist is 404, not a silent create")
    void replaceOfUnknownProgramIs404() throws Exception {
        mockMvc.perform(put("/api/catalog/programs/{code}", "T-GHOST").with(admin("ghost"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programJson("T-GHOST", "Fantasma", ProgramLevel.PREGRADO)))
                .andExpect(status().isNotFound());
    }

    // ── Deleting a program ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a program with no pensums deletes cleanly")
    void deleteProgramWithoutPensums() throws Exception {
        mockMvc.perform(post("/api/catalog/programs").with(admin("del-seed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programJson("T-DEL", "Borrable", ProgramLevel.PREGRADO)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/catalog/programs/{code}", "T-DEL").with(admin("del")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/catalog/programs/{code}", "T-DEL").with(admin("del-check")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deleting a program that still has pensums is refused, and names them")
    void deleteProgramWithPensumsIsConflictNamingThem() throws Exception {
        mockMvc.perform(delete("/api/catalog/programs/{code}", SEEDED_PROGRAM).with(admin("blocked")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[?(@.issue=='%s')]".formatted(SEEDED_PENSUM))
                        .isNotEmpty());
    }

    @Test
    @DisplayName("the refused program is still there afterwards")
    void refusedDeleteChangesNothing() throws Exception {
        mockMvc.perform(delete("/api/catalog/programs/{code}", SEEDED_PROGRAM).with(admin("noop")))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/catalog/programs/{code}", SEEDED_PROGRAM).with(admin("still-there")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("deleting an unknown program is 404")
    void deleteOfUnknownProgramIs404() throws Exception {
        mockMvc.perform(delete("/api/catalog/programs/{code}", "T-NOTHERE").with(admin("del-404")))
                .andExpect(status().isNotFound());
    }

    // ── Deleting a pensum ──────────────────────────────────────────────────────

    @Test
    @DisplayName("a pensum nobody follows deletes cleanly")
    void deletePensumWithoutStudents() throws Exception {
        mockMvc.perform(post("/api/catalog/pensums").with(admin("cur-seed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPensumJson("T-CUR")))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/catalog/pensums/{code}", "T-CUR").with(admin("cur-del")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/catalog/pensums/{code}", "T-CUR").with(admin("cur-check")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deleting a pensum a student still follows is refused with the count")
    void deletePensumWithStudentsIsConflict() throws Exception {
        mockMvc.perform(post("/api/catalog/pensums").with(admin("used-seed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPensumJson("T-USED")))
                .andExpect(status().isCreated());

        progress.save(new StudentProgress(null, "T-follower", "506900500", "506",
                "T-USED", 1, List.of(), Instant.now()));

        mockMvc.perform(delete("/api/catalog/pensums/{code}", "T-USED").with(admin("used-del")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0].field").value("students"))
                .andExpect(jsonPath("$.details[0].issue").value("1"));
    }

    @Test
    @DisplayName("the refused pensum and the student's progress both survive")
    void refusedPensumDeleteChangesNothing() throws Exception {
        mockMvc.perform(post("/api/catalog/pensums").with(admin("survive-seed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPensumJson("T-SURVIVE")))
                .andExpect(status().isCreated());
        progress.save(new StudentProgress(null, "T-survivor", "506900501", "506",
                "T-SURVIVE", 1, List.of(), Instant.now()));

        mockMvc.perform(delete("/api/catalog/pensums/{code}", "T-SURVIVE").with(admin("survive-del")))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/catalog/pensums/{code}", "T-SURVIVE").with(admin("survive-check")))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(
                1, progress.countByPensumCode("T-SURVIVE"),
                "the student's progress must not be touched by a refused delete");
    }

    @Test
    @DisplayName("deleting an unknown pensum is 404")
    void deleteOfUnknownPensumIs404() throws Exception {
        mockMvc.perform(delete("/api/catalog/pensums/{code}", "T-NEVER").with(admin("cur-404")))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private String programJson(String code, String name, ProgramLevel level) throws Exception {
        return mapper.writeValueAsString(
                new ProgramRequest(code, name, "Facultad de Pruebas", level));
    }

    private String minimalPensumJson(String pensumCode) throws Exception {
        PensumAreaDto area = new PensumAreaDto("CB", "Ciencias Basicas", "#539392", 3, 4);
        PensumCourseDto course = new PensumCourseDto(
                "M1", "M1", "Minimal Course", 1, 3, 4, null, "CB", false, List.of(), null);
        PensumDto dto = new PensumDto(pensumCode, "506", "Test Program",
                "Test Faculty", "Test Reform", PensumStatus.ACTIVE, 3, 4, 1,
                List.of(area), List.of(course));
        return mapper.writeValueAsString(dto);
    }

    private static RequestPostProcessor admin(String subject) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(b -> b.subject(subject)
                        .claim("email", subject + "@konradlorenz.edu.co")
                        .claim("roles", List.of("ROLE_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}

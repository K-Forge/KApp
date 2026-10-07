package co.edu.konradlorenz.kapp.user.sinu.fake;

import co.edu.konradlorenz.kapp.user.sinu.SinuStudent;
import co.edu.konradlorenz.kapp.user.sinu.SinuStudentPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * The test SINU: every student is the invented student of {@code docs/api/sinu/example.json} -
 * Ingeniería de Sistemas, pensum 1015, level 5 - the same one schedule-service and semaphore-service
 * serve, so a student's profile, timetable and semáforo agree.
 */
@Component
@ConditionalOnProperty(name = "kapp.sinu.adapter", havingValue = "fake", matchIfMissing = true)
public class FakeSinuStudentAdapter implements SinuStudentPort {

    /** The example's own copy, which a test keeps identical to docs/api/sinu/example.json. */
    static final String EXAMPLE = "sinu/example.json";

    private final SinuStudent student;

    public FakeSinuStudentAdapter(ObjectMapper json) {
        try (InputStream in = new ClassPathResource(EXAMPLE).getInputStream()) {
            Person p = json.readValue(in, Example.class).person();
            this.student = new SinuStudent(p.programCode(), p.programName(), p.pensumCode(), p.currentLevel());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the test SINU's student " + EXAMPLE, e);
        }
    }

    @Override
    public Optional<SinuStudent> student(String userId, String email) {
        return Optional.of(student);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Example(Person person) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Person(String programCode, String programName, String pensumCode, int currentLevel) {
    }
}

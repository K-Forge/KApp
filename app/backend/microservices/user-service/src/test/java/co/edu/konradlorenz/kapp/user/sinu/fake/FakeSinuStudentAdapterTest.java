package co.edu.konradlorenz.kapp.user.sinu.fake;

import co.edu.konradlorenz.kapp.user.sinu.SinuStudent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test SINU without a Spring context: its copy of the example, and the student it serves.
 */
class FakeSinuStudentAdapterTest {

    @Test
    @DisplayName("the service's copy of the example is docs/api/sinu/example.json, byte for byte")
    void copyMatchesTheDocs() throws Exception {
        byte[] copy = new ClassPathResource(FakeSinuStudentAdapter.EXAMPLE).getContentAsByteArray();
        byte[] docs = Files.readAllBytes(Path.of("../../../../docs/api/sinu/example.json"));
        assertThat(copy).as("copy docs/api/sinu/example.json to src/main/resources/sinu/").isEqualTo(docs);
    }

    /** The same student schedule-service and semaphore-service serve, so the three agree. */
    @Test
    @DisplayName("every student is the example's: Ingeniería de sistemas, pensum 1015, level 5")
    void everyStudentIsTheExample() {
        FakeSinuStudentAdapter sinu = new FakeSinuStudentAdapter(new ObjectMapper());

        SinuStudent expected = new SinuStudent("506", "Ingeniería de sistemas", "1015", 5);
        assertThat(sinu.student("3f8a1c2e-7b4d-4e5a-9c6f-2d1b8e0a4c73", "pepito.perez@konradlorenz.edu.co"))
                .contains(expected);
        assertThat(sinu.student("6b2d9e40-1c3f-4a57-8e69-0d1f2a3b4c5d", "laura.munoz@konradlorenz.edu.co"))
                .contains(expected);
    }
}

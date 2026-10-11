package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.semaphore.repository.PensumRepository;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuPerson;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuRecordPort;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuStudent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** The two ways a semáforo cannot be drawn, and say so with a 404 rather than an error. */
@ExtendWith(MockitoExtension.class)
class SemaphoreServiceTest {

    @Mock
    private SinuRecordPort sinu;

    @Mock
    private PensumRepository pensums;

    private final SinuPerson person = new SinuPerson("u-1", "u-1@konradlorenz.edu.co");

    private SemaphoreService service() {
        StudentRecordReader reader = new StudentRecordReader(sinu, Clock.systemUTC(), Duration.ofMinutes(5));
        return new SemaphoreService(reader, sinu, pensums, new PrerequisiteWalker());
    }

    @Test
    @DisplayName("someone SINU has no program for - not a student, or not yet one - is 404")
    void noProgramInSinu() {
        when(sinu.student(person)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().semaphore(person))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No program in SINU");
    }

    @Test
    @DisplayName("a student on a pensum the catalog does not have is 404, naming the pensum")
    void pensumNotInTheCatalog() {
        when(sinu.student(person)).thenReturn(Optional.of(new SinuStudent("999", "9999", 3)));
        when(sinu.records(person)).thenReturn(List.of());
        when(pensums.findById("9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().semaphore(person))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("9999");
    }
}

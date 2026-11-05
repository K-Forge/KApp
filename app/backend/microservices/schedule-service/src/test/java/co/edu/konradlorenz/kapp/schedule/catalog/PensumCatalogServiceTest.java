package co.edu.konradlorenz.kapp.schedule.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Which pensum item a course of the timetable is, without a Spring context: matched by SINU's code,
 * by the printed one while semaphore 1.0 knows no other, and failing open when semaphore-service is
 * down.
 */
@ExtendWith(MockitoExtension.class)
class PensumCatalogServiceTest {

    @Mock
    private CatalogClient client;

    @Test
    @DisplayName("a course is matched by its SINU code, whatever the item's printed code")
    void matchesBySinuCode() {
        assertThat(PensumCatalogService.pensumItemCode(List.of(
                new PensumCourseView("17018", "17080", "17018", "Estadística Descriptiva")), "17080")).contains("17018");
    }

    @Test
    @DisplayName("an item SINU's code is not known for is matched by its printed code")
    void matchesByPrintedCodeWhenSinuCodeIsUnknown() {
        assertThat(PensumCatalogService.pensumItemCode(List.of(
                new PensumCourseView("59035", null, "59035", "Desarrollo de Aplicaciones Móviles")), "59035")).contains("59035");
    }

    @Test
    @DisplayName("a printed code is not used once the item has a SINU code of its own")
    void printedCodeDoesNotWinOverSinuCode() {
        assertThat(PensumCatalogService.pensumItemCode(List.of(
                new PensumCourseView("17018", "17080", "17018", "Estadística Descriptiva")), "17018")).isEmpty();
    }

    @Test
    @DisplayName("a course that is not in the pensum has no item")
    void noMatch() {
        assertThat(PensumCatalogService.pensumItemCode(List.of(), "99999")).isEmpty();
    }

    @Test
    @DisplayName("a Feign failure fails open: no item, not an exception")
    void clientThrows_failsOpen() {
        when(client.listPensumCourses("1015")).thenThrow(new RuntimeException("semaphore-service is down"));

        PensumCatalogService service = new PensumCatalogService(client);

        assertThat(service.pensumCourses("1015")).isEmpty();
    }

    @Test
    @DisplayName("a section with no SINU code has no item")
    void noSinuCode() {
        assertThat(PensumCatalogService.pensumItemCode(
                List.of(new PensumCourseView("17018", "17080", "17018", "Estadística Descriptiva")), null)).isEmpty();
    }
}

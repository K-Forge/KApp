package co.edu.konradlorenz.kapp.semaphore.domain;

import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumCourseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Weekly hours: what counts as one, how they are read off a document, and how they go out.
 *
 * <p>The serialisation cases are the contract, not an implementation detail. Four items out of
 * roughly 650 print a half, and every client reading the other 646 - two of them being written
 * right now, in Kotlin and in Swift - should still see the integer it saw before.
 */
class WeeklyHoursTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("a whole hour and a half are valid; a third of an hour is not")
    void whatCountsAsAnHour() {
        assertThat(WeeklyHours.isValid(4)).isTrue();
        assertThat(WeeklyHours.isValid(4.5)).isTrue();
        assertThat(WeeklyHours.isValid(0)).isTrue();
        assertThat(WeeklyHours.isValid(4.3)).isFalse();
        assertThat(WeeklyHours.isValid(-1)).isFalse();
        assertThat(WeeklyHours.isValid(Double.NaN)).isFalse();
    }

    @Test
    @DisplayName("a figure is read as a document or a Spanish spreadsheet prints it")
    void parsesBothDecimalMarks() {
        assertThat(WeeklyHours.parse("4")).isEqualTo(4);
        assertThat(WeeklyHours.parse("4.5")).isEqualTo(4.5);
        assertThat(WeeklyHours.parse(" 4,5 ")).isEqualTo(4.5);
    }

    @Test
    @DisplayName("anything between two halves is refused where it is read, not stored")
    void refusesAnythingElse() {
        assertThatThrownBy(() -> WeeklyHours.parse("4,3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4.5");
        assertThatThrownBy(() -> WeeklyHours.parse("cuatro"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a number");
    }

    @Test
    @DisplayName("a whole hour goes out whole; only a half carries a decimal point")
    void wholeHoursStayWhole() throws Exception {
        assertThat(json.writeValueAsString(course(4))).contains("\"weeklyHours\":4,");
        assertThat(json.writeValueAsString(course(4.5))).contains("\"weeklyHours\":4.5,");
    }

    @Test
    @DisplayName("what goes out comes back the same, decimal or not")
    void roundTrips() throws Exception {
        for (double hours : new double[]{0, 4, 4.5, 1.5}) {
            PensumCourseDto read = json.readValue(json.writeValueAsString(course(hours)),
                    PensumCourseDto.class);
            assertThat(read.weeklyHours()).isEqualTo(hours);
        }
    }

    @Test
    @DisplayName("the bean-validation floor still applies to a decimal field")
    void negativeHoursAreRejectedByTheAnnotation() {
        // @Min on a double is the provider's business, not the specification's: Jakarta leaves
        // float and double out over rounding, and Hibernate Validator supplies them anyway.
        // Which is exactly why it is asserted rather than assumed - nothing else here would
        // notice if the annotation silently stopped applying.
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertThat(validator.validate(course(4.5))).isEmpty();
            assertThat(validator.validate(course(-1)))
                    .extracting(v -> v.getPropertyPath().toString())
                    .containsExactly("weeklyHours");
        }
    }

    @Test
    @DisplayName("half an hour a week is eight whole hours a semester")
    void contactHoursStayWhole() {
        assertThat(course(4.5).toDomain().totalHours()).isEqualTo(72);
        assertThat(course(1.5).toDomain().totalHours()).isEqualTo(24);
    }

    private static PensumCourseDto course(double weeklyHours) {
        return new PensumCourseDto("P5805", "P5805", "Práctica profesional", 8, 9, weeklyHours,
                null, "PROFESIONAL", false, List.of(), null);
    }
}

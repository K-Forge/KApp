package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumStatus;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumAreaDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumCourseDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for the cross-field pensum rules {@code createPensum} and
 * {@code replacePensum} both depend on.
 */
class PensumValidatorTest {

    private final PensumValidator validator = new PensumValidator();

    @Test
    @DisplayName("a structurally sound pensum passes")
    void validPensumPasses() {
        assertThat(validator).isNotNull();
        validator.validate(validPensum());
        // No exception: that is the assertion.
    }

    @Test
    @DisplayName("an elective slot with a non-null code is rejected")
    void electiveSlotWithCodeIsRejected() {
        PensumCourseDto badElective = new PensumCourseDto(
                "SHOULD-BE-NULL", "ELECTIVA_I", "Electiva I", 1, 3, 3, null, "ISA", true, List.of(), null);
        PensumDto dto = withCourses(List.of(badElective));

        assertThatThrownBy(() -> validator.validate(dto))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getDetails())
                        .anySatisfy(issue -> assertThat(issue.field()).isEqualTo("courses[0].code")));
    }

    @Test
    @DisplayName("a fixed course with a null code is rejected")
    void fixedCourseWithoutCodeIsRejected() {
        PensumCourseDto badFixed = new PensumCourseDto(
                null, "1001", "Precalculo", 1, 3, 4, null, "CB", false, List.of(), null);
        PensumDto dto = withCourses(List.of(badFixed));

        assertThatThrownBy(() -> validator.validate(dto))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getDetails())
                        .anySatisfy(issue -> assertThat(issue.field()).isEqualTo("courses[0].code")));
    }

    @Test
    @DisplayName("a course whose area is not declared by the pensum is rejected")
    void undeclaredAreaIsRejected() {
        PensumCourseDto badArea = new PensumCourseDto(
                "10011", "1001", "Precalculo", 1, 3, 4, null, "NOPE", false, List.of(), null);
        PensumDto dto = withCourses(List.of(badArea));

        assertThatThrownBy(() -> validator.validate(dto))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getDetails())
                        .anySatisfy(issue -> assertThat(issue.field()).isEqualTo("courses[0].area")));
    }

    @Test
    @DisplayName("a prerequisite that names no course code in the same document is rejected")
    void unknownPrerequisiteIsRejected() {
        PensumCourseDto badPrereq = new PensumCourseDto(
                "10024", "1102", "Calculo I", 2, 4, 5, null, "CB", false, List.of("GHOST-CODE"), null);
        PensumDto dto = withCourses(List.of(badPrereq));

        assertThatThrownBy(() -> validator.validate(dto))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getDetails())
                        .anySatisfy(issue -> assertThat(issue.field()).isEqualTo("courses[0].prerequisites")));
    }

    @Test
    @DisplayName("a supplied totalHours that breaks weeklyHours * 16 is rejected")
    void wrongTotalHoursInvariantIsRejected() {
        PensumCourseDto badHours = new PensumCourseDto(
                "10011", "1001", "Precalculo", 1, 3, 4, 999, "CB", false, List.of(), null);
        PensumDto dto = withCourses(List.of(badHours));

        assertThatThrownBy(() -> validator.validate(dto))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getDetails())
                        .anySatisfy(issue -> assertThat(issue.field()).isEqualTo("courses[0].totalHours")));
    }

    @Test
    @DisplayName("half an hour a week is accepted, and its 72 contact hours with it")
    void halfAnHourIsAccepted() {
        PensumCourseDto practice = new PensumCourseDto(
                "P5805", "P5805", "Práctica profesional", 8, 9, 4.5, 72, "CB", false, List.of(), null);
        validator.validate(withCourses(List.of(practice)));
        // No exception: that is the assertion.
    }

    @Test
    @DisplayName("a fraction of an hour that is not a half is rejected")
    void anHourAndAThirdIsRejected() {
        PensumCourseDto odd = new PensumCourseDto(
                "10011", "1001", "Precalculo", 1, 3, 4.3, null, "CB", false, List.of(), null);
        PensumDto dto = withCourses(List.of(odd));

        assertThatThrownBy(() -> validator.validate(dto))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getDetails())
                        .anySatisfy(issue -> assertThat(issue.field()).isEqualTo("courses[0].weeklyHours")));
    }

    @Test
    @DisplayName("omitting totalHours on write is not an error - the server derives it")
    void omittedTotalHoursIsAccepted() {
        PensumCourseDto noHours = new PensumCourseDto(
                "10011", "1001", "Precalculo", 1, 3, 4, null, "CB", false, List.of(), null);
        validator.validate(withCourses(List.of(noHours)));
        // No exception: that is the assertion.
    }

    private static PensumDto withCourses(List<PensumCourseDto> courses) {
        return new PensumDto("1015", "506", "Ingenieria de Sistemas",
                "Facultad de Matematicas e Ingenierias", "Reforma 2018", PensumStatus.ACTIVE,
                142, 194, 9, List.of(area("CB"), area("ISA")), courses);
    }

    private static PensumDto validPensum() {
        PensumCourseDto fixed = new PensumCourseDto(
                "10011", "1001", "Precalculo", 1, 3, 4, 64, "CB", false, List.of(), "10011");
        PensumCourseDto dependent = new PensumCourseDto(
                "10024", "1102", "Calculo I", 2, 4, 5, 80, "CB", false, List.of("10011"), "10024");
        PensumCourseDto elective = new PensumCourseDto(
                null, "ELECTIVA_I", "Electiva I", 6, 3, 3, null, "ISA", true, List.of(), null);
        return withCourses(List.of(fixed, dependent, elective));
    }

    private static PensumAreaDto area(String code) {
        return new PensumAreaDto(code, "Area " + code, "#539392", 10, 10);
    }
}

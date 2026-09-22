package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumCourse;
import co.edu.konradlorenz.kapp.semaphore.domain.WeeklyHours;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumAreaDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumCourseDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The cross-field rules {@code PensumDto}'s bean validation cannot see on its own,
 * because each one depends on more than one field or on a sibling item in the same
 * document. Shared by {@code createPensum} and {@code replacePensum} so an admin
 * cannot create an inconsistent pensum through one path that the other would refuse.
 */
@Component
public class PensumValidator {

    /**
     * @throws BusinessRuleException with one {@link ApiError.FieldIssue} per violation,
     *                                naming the offending item by its position, so an
     *                                admin UI can point at the exact row
     */
    public void validate(PensumDto dto) {
        List<ApiError.FieldIssue> issues = new ArrayList<>();

        Set<String> areaCodes = dto.areas().stream().map(PensumAreaDto::code).collect(Collectors.toSet());
        Set<String> courseCodes = dto.courses().stream()
                .map(PensumCourseDto::code)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<PensumCourseDto> courses = dto.courses();
        for (int i = 0; i < courses.size(); i++) {
            PensumCourseDto course = courses.get(i);
            String prefix = "courses[%d]".formatted(i);

            if (course.isElectiveSlot() && course.code() != null) {
                issues.add(new ApiError.FieldIssue(prefix + ".code",
                        "must be null for an elective slot"));
            }
            if (!course.isElectiveSlot() && course.code() == null) {
                issues.add(new ApiError.FieldIssue(prefix + ".code",
                        "must not be null for a fixed course"));
            }
            if (!areaCodes.contains(course.area())) {
                issues.add(new ApiError.FieldIssue(prefix + ".area",
                        "'%s' is not one of the declared areas".formatted(course.area())));
            }
            // A plan prints whole hours or halves and nothing else, so a 4.3 is a
            // transcription slip. Caught here rather than by an annotation because bean
            // validation has no "multiple of" for a decimal.
            if (!WeeklyHours.isValid(course.weeklyHours())) {
                issues.add(new ApiError.FieldIssue(prefix + ".weeklyHours",
                        "must be a whole number of hours or a half, such as 4 or 4.5"));
            }
            if (course.totalHours() != null
                    && course.totalHours() != Math.round(
                            course.weeklyHours() * PensumCourse.WEEKS_PER_SEMESTER)) {
                issues.add(new ApiError.FieldIssue(prefix + ".totalHours",
                        "must equal weeklyHours * %d when supplied".formatted(PensumCourse.WEEKS_PER_SEMESTER)));
            }
            for (String prerequisite : course.prerequisites()) {
                if (!courseCodes.contains(prerequisite)) {
                    issues.add(new ApiError.FieldIssue(prefix + ".prerequisites",
                            "'%s' is not the code of any course in this pensum".formatted(prerequisite)));
                }
            }
        }

        if (!issues.isEmpty()) {
            throw new BusinessRuleException("Pensum failed validation", issues);
        }
    }
}

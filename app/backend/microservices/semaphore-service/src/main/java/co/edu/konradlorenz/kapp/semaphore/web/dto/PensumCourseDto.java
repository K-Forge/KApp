package co.edu.konradlorenz.kapp.semaphore.web.dto;

import co.edu.konradlorenz.kapp.semaphore.domain.PensumCourse;
import co.edu.konradlorenz.kapp.semaphore.domain.WeeklyHours;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One pensum item on the wire.
 *
 * <p>{@code totalHours} is nullable on the way in - the catalog import builds items without it -
 * and always present on read: omitted, the server derives {@code weeklyHours * 16}; supplied, it
 * must satisfy that invariant or the item is rejected. That is why it is validated in the service
 * rather than here - a bean-validation annotation cannot see a sibling field.
 *
 * <p>{@code weeklyHours} is a number rather than an integer, because four practices across the
 * published plans print half an hour - see {@link co.edu.konradlorenz.kapp.semaphore.domain.WeeklyHours}.
 * Whole hours are still written whole ({@code 4}, not {@code 4.0}), so those four items are the
 * only ones whose hours carry a decimal point. {@code totalHours} stays an integer: half an hour
 * a week is eight whole hours a semester.
 *
 * <p>{@code sinuCode} is the field a client puts on screen, and {@code pensumItemCode} the one it
 * addresses items by. Nineteen of the twenty-three published plans print no course codes at all,
 * so their item codes are something this system generated to tell the items apart;
 * {@code sinuCode} is present only where the code is the university's own. Showing a generated
 * code would put an invented identifier in front of a student, who has no way to tell it from a
 * real one - the contract says so where the clients read it,
 * {@code docs/api/semaphore.openapi.yaml}.
 */
public record PensumCourseDto(
        @NotBlank @Size(max = 30) String pensumItemCode,
        @NotBlank @Size(max = 120) String name,
        @Min(1) @Max(12) int level,
        @Min(0) int credits,
        @Min(0) @JsonSerialize(using = WeeklyHours.Serializer.class) double weeklyHours,
        @Min(0) Integer totalHours,
        @NotBlank @Size(max = 20) String area,
        @JsonProperty("isElectiveSlot") boolean isElectiveSlot,
        @NotNull List<@Size(max = 30) String> prerequisites,
        @Size(max = 20) String sinuCode
) {

    public static PensumCourseDto from(PensumCourse course) {
        return new PensumCourseDto(
                course.pensumItemCode(),
                course.name(),
                course.level(),
                course.credits(),
                course.weeklyHours(),
                course.totalHours(),
                course.area(),
                course.electiveSlot(),
                course.prerequisites(),
                course.sinuCode());
    }

    public PensumCourse toDomain() {
        return new PensumCourse(pensumItemCode, name, level, credits, weeklyHours,
                area, isElectiveSlot, prerequisites, sinuCode);
    }
}

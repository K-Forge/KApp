package co.edu.konradlorenz.kapp.semaphore.web.dto;

import co.edu.konradlorenz.kapp.semaphore.domain.Pensum;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.WeeklyHours;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One pensum, without its courses.
 *
 * <p>There was no way to list pensums at all until this existed: the admin portal asked you to
 * type a code from memory, and a screen that cannot show you what it holds is not a screen you
 * can manage anything from.
 *
 * <p>Deliberately a summary. Twenty-four pensums with sixty courses each is roughly fifteen
 * hundred course objects, and a dropdown needs none of them - it needs a code and something
 * readable next to it. The full document is one call away at
 * {@code GET /api/catalog/pensums/{pensumCode}}.
 */
@Schema(description = "A pensum without its courses, for listings and pickers.")
public record PensumSummary(
        String pensumCode,
        String programCode,
        String programName,
        String faculty,
        String reform,
        PensumStatus status,
        int totalCredits,
        @JsonSerialize(using = WeeklyHours.Serializer.class) double totalHours,
        int levels,
        @Schema(description = "How many items the pensum carries, without sending them.")
        int courses
) {

    public static PensumSummary from(Pensum c) {
        return new PensumSummary(c.pensumCode(), c.programCode(), c.programName(),
                c.faculty(), c.reform(), c.status(), c.totalCredits(), c.totalHours(),
                c.levels(), c.courses().size());
    }
}

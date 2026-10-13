package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** The {@code Survey} schema: the distances taken round a campus's blocks. */
@Schema(name = "Survey", description = "The distances taken on site round a campus's blocks.")
public record SurveyResponse(
        @Schema(example = "Sede Principal") String campus,
        List<SurveyMeasureDto> measures,
        @Schema(description = "Bumped by every save. Send it back with the next one.", example = "3") long version,
        @Schema(description = "When it was last saved; absent while nothing has been.") Instant updatedAt
) {
}

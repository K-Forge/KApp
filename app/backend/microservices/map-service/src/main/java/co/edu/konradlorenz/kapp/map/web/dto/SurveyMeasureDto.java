package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** The {@code SurveyMeasure} schema: one distance taken on site. */
@Schema(name = "SurveyMeasure", description = "One distance taken on site round a campus's blocks, with a tape or "
        + "a phone's Measure app: how far a wall stands from the curb, or how long it is.")
public record SurveyMeasureDto(
        @Schema(description = "Which distance of the portal's survey plan it is, or an extra one's own id.",
                example = "bis-05")
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,39}") String id,
        @Schema(description = "What an extra distance is of; absent for one the plan names.",
                example = "Width of the gate on Cra 9A")
        @Size(max = 200) String label,
        @Schema(description = "What was typed, as it was typed: pieces joined with +.", example = "4,80 + 3,25")
        @Size(max = 120) String text,
        @Schema(description = "The total the portal read from the text, in metres; absent while it reads none.",
                example = "8.05")
        @DecimalMin("0") @DecimalMax("1000") Double metres,
        @Schema(description = "What was seen on site that the sketch does not show.",
                example = "Columns in front of the wall: measured to their face")
        @Size(max = 500) String note,
        @Schema(description = "When it was last changed. Set by the server; ignored in a request.",
                accessMode = Schema.AccessMode.READ_ONLY)
        Instant updatedAt
) {
}

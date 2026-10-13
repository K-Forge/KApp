package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The {@code SurveyRequest} schema: every distance taken, as the portal saves them. */
@Schema(name = "SurveyRequest", description = "Every distance taken round a campus's blocks, the whole list, with "
        + "the version it was read at.")
public record SurveyRequest(
        @Schema(description = "The version the survey was read at; 0 while nothing has been saved.", example = "3")
        @NotNull Long version,
        @NotNull @Size(max = 500) List<@NotNull @Valid SurveyMeasureDto> measures
) {
}

package co.edu.konradlorenz.kapp.map.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** The {@code GeoPoint} schema: a point on the earth. */
@Schema(name = "GeoPoint", description = "A point on the earth, in degrees of WGS 84.")
public record GeoPointDto(
        @Schema(example = "4.64863") @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @Schema(example = "-74.06115") @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon
) {
}

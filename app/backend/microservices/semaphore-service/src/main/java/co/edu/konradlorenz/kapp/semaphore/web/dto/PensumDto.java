package co.edu.konradlorenz.kapp.semaphore.web.dto;

import co.edu.konradlorenz.kapp.semaphore.domain.Pensum;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.WeeklyHours;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PensumDto(
        @NotBlank @Size(max = 20) String pensumCode,
        @NotBlank @Size(max = 20) String programCode,
        @NotBlank @Size(max = 100) String programName,
        @NotBlank @Size(max = 100) String faculty,
        @NotBlank @Size(max = 100) String reform,
        @NotNull PensumStatus status,
        @Min(0) int totalCredits,
        @Min(0) @JsonSerialize(using = WeeklyHours.Serializer.class) double totalHours,
        @Min(1) @Max(12) int levels,
        @NotEmpty @Valid List<PensumAreaDto> areas,
        @NotEmpty @Valid List<PensumCourseDto> courses
) {

    public static PensumDto from(Pensum pensum) {
        return new PensumDto(
                pensum.pensumCode(),
                pensum.programCode(),
                pensum.programName(),
                pensum.faculty(),
                pensum.reform(),
                pensum.status(),
                pensum.totalCredits(),
                pensum.totalHours(),
                pensum.levels(),
                pensum.areas().stream().map(PensumAreaDto::from).toList(),
                pensum.coursesInDisplayOrder().stream().map(PensumCourseDto::from).toList());
    }

    public Pensum toDomain() {
        return new Pensum(
                pensumCode, programCode, programName, faculty, reform, status,
                totalCredits, totalHours, levels,
                areas.stream().map(PensumAreaDto::toDomain).toList(),
                courses.stream().map(PensumCourseDto::toDomain).toList());
    }
}

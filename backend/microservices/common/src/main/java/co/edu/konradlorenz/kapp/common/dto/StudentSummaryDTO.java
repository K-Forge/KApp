package co.edu.konradlorenz.kapp.common.dto;

import lombok.Data;

/**
 * DTO con resumen de información de estudiante.
 */
@Data
public class StudentSummaryDTO {
    private Long id;
    private String fullName;
    private String studentCode;
    private String email;
}

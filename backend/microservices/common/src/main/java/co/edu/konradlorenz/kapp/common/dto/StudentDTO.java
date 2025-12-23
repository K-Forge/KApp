package co.edu.konradlorenz.kapp.common.dto;

import lombok.Data;

/**
 * DTO para transferencia de datos de Estudiante.
 */
@Data
public class StudentDTO {
    private Long id;
    private Long memberId;
    private String studentCode;
}

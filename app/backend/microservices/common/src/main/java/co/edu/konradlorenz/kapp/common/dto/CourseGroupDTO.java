package co.edu.konradlorenz.kapp.common.dto;

import lombok.Data;

/**
 * DTO para transferencia de datos de Grupos de Curso.
 */
@Data
public class CourseGroupDTO {
    private Long id;
    private Long courseId;
    private Long periodId;
    private Long professorId;
    private String groupCode;
}

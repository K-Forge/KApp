package co.edu.konradlorenz.kapp.common.dto;

import lombok.Data;

/**
 * DTO para la respuesta de cursos inscritos/asignados.
 */
@Data
public class CourseDTO {
    private Long courseGroupId;
    private String courseName;
    private String courseCode;
    private String groupCode;
    private String professorName;
}

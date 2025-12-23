package co.edu.konradlorenz.kapp.common.dto;

import lombok.Data;

/**
 * DTO para operaciones CRUD de cursos.
 */
@Data
public class CourseCrudDTO {
    private Long id;
    private Long programId;
    private String code;
    private String name;
    private Integer credits;
    private Integer level;
}

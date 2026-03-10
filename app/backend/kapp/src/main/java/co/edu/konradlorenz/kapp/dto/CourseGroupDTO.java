package co.edu.konradlorenz.kapp.dto;

import lombok.Data;

@Data
public class CourseGroupDTO {
    private Long id;
    private Long courseId;
    private Long periodId;
    private Long professorId;
    private String groupCode;
}

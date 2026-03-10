package co.edu.konradlorenz.kapp.dto;

import lombok.Data;

@Data
public class CourseDTO {
    private Long courseGroupId;
    private String courseName;
    private String courseCode;
    private String groupCode;
    private String professorName;
}

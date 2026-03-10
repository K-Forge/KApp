package co.edu.konradlorenz.kapp.dto;

import lombok.Data;

@Data
public class CourseCrudDTO {
    private Long id;
    private Long programId;
    private String code;
    private String name;
    private Integer credits;
    private Integer level;
}

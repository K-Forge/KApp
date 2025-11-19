package co.edu.konradlorenz.kapp.dto;

import lombok.Data;
import java.util.Date;

@Data
public class AssignmentDTO {
    private Long id;
    private Long courseGroupId;
    private String title;
    private String description;
    private Date dueDate;
    private Double maxScore;
}

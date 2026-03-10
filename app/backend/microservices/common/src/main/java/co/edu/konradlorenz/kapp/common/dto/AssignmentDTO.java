package co.edu.konradlorenz.kapp.common.dto;

import lombok.Data;
import java.util.Date;

/**
 * DTO para transferencia de datos de Tareas.
 */
@Data
public class AssignmentDTO {
    private Long id;
    private Long courseGroupId;
    private String title;
    private String description;
    private Date dueDate;
    private Double maxScore;
}

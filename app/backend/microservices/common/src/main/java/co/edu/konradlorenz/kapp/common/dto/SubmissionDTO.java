package co.edu.konradlorenz.kapp.common.dto;

import lombok.Data;
import java.util.Date;

/**
 * DTO para transferencia de datos de Entregas.
 */
@Data
public class SubmissionDTO {
    private Long id;
    private Long assignmentId;
    private Long studentId;
    private String studentName;
    private Date submissionDate;
    private String contentUrl;
    private Double grade;
    private String feedback;
}

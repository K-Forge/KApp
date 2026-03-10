package co.edu.konradlorenz.kapp.dto;

import lombok.Data;

@Data
public class StudentSummaryDTO {
    private Long id;
    private String fullName;
    private String studentCode;
    private String email;
}

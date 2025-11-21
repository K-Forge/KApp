package co.edu.konradlorenz.kapp.dto;

import lombok.Data;

@Data
public class MemberDTO {
    private Long id;
    private Long personId;
    private String universityCode;
    private String universityEmail;
    private String password;
}

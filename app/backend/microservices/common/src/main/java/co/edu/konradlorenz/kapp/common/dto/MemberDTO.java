package co.edu.konradlorenz.kapp.common.dto;

import lombok.Data;

/**
 * DTO para transferencia de datos de Miembro.
 */
@Data
public class MemberDTO {
    private Long id;
    private Long personId;
    private String universityCode;
    private String universityEmail;
    private String password;
}

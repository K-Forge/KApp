package co.edu.konradlorenz.kapp.common.dto;

import lombok.Data;

/**
 * DTO para solicitud de inicio de sesión.
 */
@Data
public class LoginRequest {
    private String email;
    private String password;
}

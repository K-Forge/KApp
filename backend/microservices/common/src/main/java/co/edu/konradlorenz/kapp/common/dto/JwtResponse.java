package co.edu.konradlorenz.kapp.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para respuesta de autenticación JWT.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JwtResponse {
    private String token;
    private String type = "Bearer";
    private String username;
    private String role;

    public JwtResponse(String token, String username, String role) {
        this.token = token;
        this.type = "Bearer";
        this.username = username;
        this.role = role;
    }
}

package co.edu.konradlorenz.kapp.auth.controller;

import co.edu.konradlorenz.kapp.auth.service.AuthService;
import co.edu.konradlorenz.kapp.common.dto.JwtResponse;
import co.edu.konradlorenz.kapp.common.dto.LoginRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador de autenticación.
 * Expone endpoints para login y gestión de tokens.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Endpoint de inicio de sesión.
     * 
     * @param loginRequest Credenciales del usuario
     * @return Token JWT y datos del usuario
     */
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    /**
     * Endpoint para verificar el estado del servicio.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth Service is running");
    }
}

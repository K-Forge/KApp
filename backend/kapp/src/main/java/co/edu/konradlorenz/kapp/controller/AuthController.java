package co.edu.konradlorenz.kapp.controller;

import co.edu.konradlorenz.kapp.dto.JwtResponse;
import co.edu.konradlorenz.kapp.dto.LoginRequest;
import co.edu.konradlorenz.kapp.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }
}

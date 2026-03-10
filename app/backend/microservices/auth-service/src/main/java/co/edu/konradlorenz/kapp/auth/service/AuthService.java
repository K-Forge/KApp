package co.edu.konradlorenz.kapp.auth.service;

import co.edu.konradlorenz.kapp.auth.security.JwtTokenProvider;
import co.edu.konradlorenz.kapp.common.dto.JwtResponse;
import co.edu.konradlorenz.kapp.common.dto.LoginRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Servicio de autenticación.
 * Maneja la lógica de inicio de sesión y generación de tokens.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

        private final AuthenticationManager authenticationManager;
        private final JwtTokenProvider tokenProvider;

        /**
         * Autentica al usuario y genera un token JWT.
         */
        public JwtResponse login(LoginRequest loginRequest) {
                Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                loginRequest.getEmail(),
                                                loginRequest.getPassword()));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                String jwt = tokenProvider.generateToken(authentication);

                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                String role = userDetails.getAuthorities().stream()
                                .findFirst()
                                .map(item -> item.getAuthority())
                                .orElse("ROLE_USER");

                return new JwtResponse(jwt, userDetails.getUsername(), role);
        }
}

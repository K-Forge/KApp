package co.edu.konradlorenz.kapp.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An account an administrator creates, mirroring {@code AccountRequest} in
 * docs/api/auth.openapi.yaml. A student needs its codes; a professor's are ignored.
 */
public record AccountRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Pattern(regexp = "ROLE_STUDENT|ROLE_PROFESSOR", message = "must be ROLE_STUDENT or ROLE_PROFESSOR") String role,
        @Pattern(regexp = "\\d{6,20}") String studentCode,
        @Pattern(regexp = "\\d{1,10}") String programCode) {
}

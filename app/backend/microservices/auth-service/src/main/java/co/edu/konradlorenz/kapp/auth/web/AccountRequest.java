package co.edu.konradlorenz.kapp.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An account an administrator creates with a temporary password, mirroring {@code AccountRequest}
 * in docs/api/auth.openapi.yaml. Development only. The profile role is the account's; no
 * permission is granted this way.
 */
public record AccountRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Pattern(regexp = "ROLE_STUDENT|ROLE_PROFESSOR|ROLE_STAFF",
                message = "must be ROLE_STUDENT, ROLE_PROFESSOR or ROLE_STAFF") String profileRole) {
}

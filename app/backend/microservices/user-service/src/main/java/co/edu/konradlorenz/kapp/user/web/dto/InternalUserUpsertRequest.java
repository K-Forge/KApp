package co.edu.konradlorenz.kapp.user.web.dto;

import co.edu.konradlorenz.kapp.user.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of {@code POST /internal/users}: what auth-service knows about an account - the names
 * Microsoft gives and the roles it grants.
 *
 * <p>{@code email} is the natural key of the upsert, which is why a repeated call updates rather
 * than duplicates and a sign-in can be replayed after a timeout. That the roles hold exactly one
 * profile role is checked in the service, since it concerns the list as a whole.
 */
public record InternalUserUpsertRequest(

        @NotBlank(message = "must not be blank")
        @Email(message = "must be a well-formed e-mail address")
        @Size(max = 100, message = "must be at most 100 characters")
        String email,

        @NotBlank(message = "must not be blank")
        @Size(min = 1, max = 50, message = "must be between 1 and 50 characters")
        String firstName,

        @NotBlank(message = "must not be blank")
        @Size(min = 1, max = 50, message = "must be between 1 and 50 characters")
        String lastName,

        @NotEmpty(message = "must hold the profile role")
        List<@NotNull(message = "must not hold null") UserRole> roles
) {
}

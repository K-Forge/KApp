package co.edu.konradlorenz.kapp.user.web.dto;

import co.edu.konradlorenz.kapp.user.domain.UserRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Body of {@code PUT /internal/users/{id}/roles}: an account's roles, as auth-service now grants them. */
public record InternalUserRolesRequest(
        @NotEmpty(message = "must hold the profile role")
        List<@NotNull(message = "must not hold null") UserRole> roles
) {
}

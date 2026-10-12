package co.edu.konradlorenz.kapp.auth.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of {@code PUT /auth/admin/accounts/{userId}/roles}: exactly the roles the account should
 * hold. What each value may be is checked in {@code AccountRolesService}, which names the field.
 *
 * @param profileRole what the app shows the person: student, professor or staff
 * @param permissions every part of the portal they may open; none is an empty list
 */
public record AccountRolesRequest(
        @NotNull(message = "is required") String profileRole,
        @NotNull(message = "is required: send an empty list for none")
        @Size(max = 5, message = "must hold at most 5")
        List<String> permissions
) {
}

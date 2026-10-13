package co.edu.konradlorenz.kapp.auth.client;

import java.util.List;

/** Body of {@code PUT /internal/users/{id}/roles} on user-service: an account's roles now. */
public record InternalUserRoles(List<String> roles) {
}

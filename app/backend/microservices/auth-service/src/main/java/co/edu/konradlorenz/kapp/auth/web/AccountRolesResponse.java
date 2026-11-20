package co.edu.konradlorenz.kapp.auth.web;

import java.util.List;

/** The {@code AccountRoles} schema: the account's roles now, the profile role first. */
public record AccountRolesResponse(String userId, List<String> roles) {
}

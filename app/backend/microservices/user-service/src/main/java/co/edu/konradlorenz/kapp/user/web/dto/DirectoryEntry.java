package co.edu.konradlorenz.kapp.user.web.dto;

import co.edu.konradlorenz.kapp.user.domain.UserProfile;
import co.edu.konradlorenz.kapp.user.domain.UserRole;

import java.util.List;

/**
 * The {@code DirectoryEntry} schema: an account as the directory shows it - who it is, what it may
 * do, and whether it is active. Nothing academic, nothing private: an administrator manages
 * accounts, not records.
 *
 * <p>{@code avatarUrl} is written as null rather than left out, because the contract lists it as
 * required.
 */
public record DirectoryEntry(
        String id,
        String email,
        String firstName,
        String lastName,
        String avatarUrl,
        List<UserRole> roles,
        boolean active
) {

    public static DirectoryEntry from(UserProfile profile) {
        return new DirectoryEntry(profile.id(), profile.email(), profile.firstName(), profile.lastName(),
                profile.avatarUrl(), profile.roles(), profile.active());
    }
}

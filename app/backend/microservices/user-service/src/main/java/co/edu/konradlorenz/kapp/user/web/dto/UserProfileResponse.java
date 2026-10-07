package co.edu.konradlorenz.kapp.user.web.dto;

import co.edu.konradlorenz.kapp.user.domain.UserProfile;
import co.edu.konradlorenz.kapp.user.domain.UserRole;

import java.util.List;

/**
 * The {@code UserProfile} schema: a person's own profile, their directory entry plus the academic
 * block when they are a student. Only ever returned to the person themselves.
 *
 * <p>{@code academic} is written as null for everybody who is not a student, rather than left out:
 * the contract lists it as required, so a client is told the field exists and is empty.
 */
public record UserProfileResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String avatarUrl,
        List<UserRole> roles,
        boolean active,
        AcademicInfoResponse academic
) {

    public static UserProfileResponse from(UserProfile profile, AcademicInfoResponse academic) {
        return new UserProfileResponse(profile.id(), profile.email(), profile.firstName(), profile.lastName(),
                profile.avatarUrl(), profile.roles(), profile.active(), academic);
    }
}

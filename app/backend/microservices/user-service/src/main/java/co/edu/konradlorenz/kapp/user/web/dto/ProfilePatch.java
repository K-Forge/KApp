package co.edu.konradlorenz.kapp.user.web.dto;

/**
 * The one thing a person changes about their own profile: their picture.
 *
 * @param avatarUrl the new picture's absolute URL, or null to clear it
 */
public record ProfilePatch(String avatarUrl) {
}

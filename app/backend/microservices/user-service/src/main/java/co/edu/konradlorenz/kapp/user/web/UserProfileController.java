package co.edu.konradlorenz.kapp.user.web;

import co.edu.konradlorenz.kapp.common.security.CurrentUser;
import co.edu.konradlorenz.kapp.user.security.NotGuest;
import co.edu.konradlorenz.kapp.user.service.UserProfileService;
import co.edu.konradlorenz.kapp.user.web.dto.UserProfileResponse;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service access to the caller's own profile.
 *
 * <p>Neither method takes a user id. The target is always the {@code sub} claim of the
 * validated token, so there is no parameter an attacker could point at somebody else's
 * account, and no ownership check that could be forgotten.
 *
 * <p><strong>{@code ROLE_GUEST} is refused.</strong> It used to be allowed, back when a guest
 * was an account with a profile whose {@code academic} block was null. A guest is now a
 * visitor holding a day pass: there is no account, the token's subject is
 * {@code visitor:<pass id>}, and this endpoint answered every such call with {@code 404}
 * because no profile could ever match. Refusing it outright is both more honest and what
 * makes "the pass opens the campus map and nothing else" literally true rather than true in
 * effect.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Profile")
public class UserProfileController {

    private final UserProfileService users;
    private final ProfilePatchReader patchReader;

    public UserProfileController(UserProfileService users, ProfilePatchReader patchReader) {
        this.users = users;
        this.patchReader = patchReader;
    }

    @GetMapping("/me")
    @NotGuest
    @Operation(summary = "Get the authenticated user's profile",
            description = "Resolved from the token's sub claim. academic, read from SINU, is null for "
                    + "anybody who is not a student.")
    public UserProfileResponse me() {
        return users.me(CurrentUser.id());
    }

    /**
     * Binds the raw JSON tree on purpose, so that every field sent can be seen and named:
     * {@link ProfilePatchReader} takes {@code avatarUrl} and refuses anything else with a reason.
     */
    @PatchMapping(path = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    @NotGuest
    @Operation(summary = "Update the authenticated user's profile",
            description = "Changes the caller's own picture; null clears it. Any other field is "
                    + "rejected with 400, naming why.")
    public UserProfileResponse updateMe(@RequestBody JsonNode body) {
        return users.update(CurrentUser.id(), patchReader.read(body));
    }
}

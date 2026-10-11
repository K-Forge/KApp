package co.edu.konradlorenz.kapp.user.web;

import co.edu.konradlorenz.kapp.user.service.UserProfileService;
import co.edu.konradlorenz.kapp.user.web.dto.InternalUserUpsertRequest;
import co.edu.konradlorenz.kapp.user.web.dto.DirectoryEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one endpoint another service calls directly.
 *
 * <p>auth-service invokes it during registration, at the moment when the account exists
 * as a credential but has no profile and no token has been issued for it - so there is no
 * JWT to forward and the usual propagation has nothing to send. Authentication is the
 * shared {@code X-Internal-Token} secret instead, checked by
 * {@code InternalTokenAuthenticationFilter} in a chain of its own.
 *
 * <p>The gateway does not route {@code /internal/**}, so this path is only reachable from
 * inside the Docker network. No web, Kotlin or Swift client should ever call it.
 *
 * <p>It answers 200 whether it created or updated, and never 409, which is what makes a
 * replayed registration safe. Deleting is idempotent the same way: 204 whether the profile was
 * there or not, so a deletion that timed out can be sent again.
 */
@RestController
@RequestMapping("/internal/users")
@Tag(name = "Internal")
public class InternalUserController {

    private final UserProfileService users;

    public InternalUserController(UserProfileService users) {
        this.users = users;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create or update a profile from another service",
            description = "Idempotent upsert keyed by e-mail. Returns 200 in both cases.")
    public DirectoryEntry upsert(@Valid @RequestBody InternalUserUpsertRequest request) {
        return users.upsertFromAuth(request);
    }

    /** auth-service removing an account an administrator deleted: its credential is already gone. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a profile from another service",
            description = "Idempotent. Returns 204 whether or not the profile existed.")
    public void delete(@PathVariable String id) {
        users.deleteForAccount(id);
    }
}

package co.edu.konradlorenz.kapp.user.web;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.common.security.KappRoles;
import co.edu.konradlorenz.kapp.user.domain.UserRole;
import co.edu.konradlorenz.kapp.user.service.UserProfileService;
import co.edu.konradlorenz.kapp.user.web.dto.PageResponse;
import co.edu.konradlorenz.kapp.user.web.dto.DirectoryEntry;
import co.edu.konradlorenz.kapp.user.web.dto.UserStatusUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * The directory and activation state. The only place in the service where one account
 * can see another, which is why every method is behind {@code ROLE_ADMIN}.
 *
 * <p>An administrator reading their own profile still uses {@code GET /api/users/me};
 * this controller is about other people's.
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('" + KappRoles.Short.ADMIN + "')")
@Tag(name = "Administration")
public class UserAdminController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_QUERY_LENGTH = 100;

    private final UserProfileService users;

    public UserAdminController(UserProfileService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "List and search user profiles",
            description = "Filters combine with AND. Search is case and accent insensitive "
                    + "and matches word prefixes of the first name, last name and e-mail.")
    public PageResponse list(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size,
                             @RequestParam(required = false) UserRole role,
                             @RequestParam(required = false) Boolean active,
                             @RequestParam(required = false) String q) {
        validatePaging(page, size, q);
        return users.search(role, active, q, page, size);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get a user profile by id",
            description = "A well-formed but unknown id is a 404, not a 400.")
    public DirectoryEntry byId(@PathVariable String userId) {
        return users.entry(userId);
    }

    @PatchMapping(path = "/{userId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Activate or deactivate a user",
            description = "A logical delete: the profile is kept for audit, but the account can "
                    + "no longer use the platform. Idempotent.")
    public DirectoryEntry setStatus(@PathVariable String userId,
                                         @Valid @RequestBody UserStatusUpdateRequest request) {
        return users.setActive(userId, request.active());
    }

    /**
     * Rejects an out-of-range page request rather than clamping it.
     *
     * <p>Clamping is the tempting alternative and it is a lie: a client that asks for 5000
     * profiles and receives 100 with no indication of the cap concludes that 100 is all
     * there are. The contract chose 400 for that reason.
     */
    private void validatePaging(int page, int size, String q) {
        List<ApiError.FieldIssue> issues = new ArrayList<>();

        if (page < 0) {
            issues.add(new ApiError.FieldIssue("page", "must be zero or greater"));
        }
        if (size < 1) {
            issues.add(new ApiError.FieldIssue("size", "must be at least 1"));
        } else if (size > MAX_PAGE_SIZE) {
            issues.add(new ApiError.FieldIssue("size",
                    "must be at most " + MAX_PAGE_SIZE));
        }
        if (q != null && q.isEmpty()) {
            issues.add(new ApiError.FieldIssue("q", "must not be empty"));
        } else if (q != null && q.length() > MAX_QUERY_LENGTH) {
            issues.add(new ApiError.FieldIssue("q",
                    "must be at most " + MAX_QUERY_LENGTH + " characters"));
        }

        if (!issues.isEmpty()) {
            throw new BusinessRuleException("Validation failed", issues);
        }
    }
}

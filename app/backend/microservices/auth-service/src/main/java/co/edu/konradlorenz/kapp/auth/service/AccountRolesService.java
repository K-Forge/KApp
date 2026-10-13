package co.edu.konradlorenz.kapp.auth.service;

import co.edu.konradlorenz.kapp.auth.client.InternalUserRoles;
import co.edu.konradlorenz.kapp.auth.client.UserProfileClient;
import co.edu.konradlorenz.kapp.auth.domain.Credential;
import co.edu.konradlorenz.kapp.auth.domain.CredentialRepository;
import co.edu.konradlorenz.kapp.auth.error.ProfileServiceUnavailableException;
import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.common.security.KappRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An account's profile role and permissions, as an administrator sets them in the portal.
 *
 * <p>The roles are replaced, not merged: what is sent is what the account holds. They reach the
 * person's tokens at the next renewal - within the hour - because a renewal signs the account's
 * roles at that moment. The directory in user-service is told as well, so it shows them.
 */
@Service
public class AccountRolesService {

    private static final Logger log = LoggerFactory.getLogger(AccountRolesService.class);

    private final CredentialRepository credentials;
    private final UserProfileClient userProfiles;

    public AccountRolesService(CredentialRepository credentials, UserProfileClient userProfiles) {
        this.credentials = credentials;
        this.userProfiles = userProfiles;
    }

    /**
     * @return the account's roles now: the profile role, then the permissions in a fixed order
     * @throws BusinessRuleException              (400) for a value that is not a profile role or
     *                                            not a permission, a permission listed twice, or an
     *                                            administrator taking {@code ROLE_ADMIN} away from
     *                                            themselves
     * @throws ResourceNotFoundException          (404) when no account carries that id
     * @throws ProfileServiceUnavailableException (503) when the roles are saved but the directory
     *                                            could not be told; sending them again finishes it
     */
    public List<String> set(String userId, String profileRole, List<String> permissions, String requestedBy) {
        List<ApiError.FieldIssue> issues = new ArrayList<>();
        if (!KappRoles.PROFILE_ROLES.contains(profileRole)) {
            issues.add(new ApiError.FieldIssue("profileRole",
                    "must be one of " + String.join(", ", KappRoles.PROFILE_ROLES)));
        }
        Set<String> seen = new HashSet<>();
        for (String permission : permissions) {
            if (KappRoles.PROFILE_ROLES.contains(permission)) {
                issues.add(new ApiError.FieldIssue("permissions",
                        permission + " is a profile role, not a permission: send it as profileRole"));
            } else if (!KappRoles.PERMISSIONS.contains(permission)) {
                issues.add(new ApiError.FieldIssue("permissions", permission + " is not a permission"));
            } else if (!seen.add(permission)) {
                issues.add(new ApiError.FieldIssue("permissions", permission + " is listed twice"));
            }
        }
        if (!issues.isEmpty()) {
            throw new BusinessRuleException("Validation failed", issues);
        }

        Credential account = credentials.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", userId));
        if (userId.equals(requestedBy) && account.roles().contains(KappRoles.ADMIN)
                && !permissions.contains(KappRoles.ADMIN)) {
            // The portal must always keep somebody who can manage it.
            throw new BusinessRuleException("You cannot take ROLE_ADMIN away from yourself",
                    List.of(new ApiError.FieldIssue("permissions", "must keep ROLE_ADMIN on your own account")));
        }

        List<String> roles = new ArrayList<>();
        roles.add(profileRole);
        KappRoles.PERMISSIONS.stream().filter(permissions::contains).forEach(roles::add);

        if (!roles.equals(account.roles()) || !account.profileRoleIsAdministrators()) {
            // From now on the profile role is the administrator's: a sign-in with Microsoft no
            // longer replaces it with what the directory says.
            credentials.save(account.withRoles(roles, true, Instant.now()));
            log.info("Roles of account {} set to {} by {}", userId, roles, requestedBy);
        }
        try {
            userProfiles.setRoles(userId, new InternalUserRoles(roles));
        } catch (RuntimeException e) {
            throw new ProfileServiceUnavailableException(
                    "The roles are saved, but the directory could not show them yet. Send them again in a moment.", e);
        }
        return List.copyOf(roles);
    }
}

package co.edu.konradlorenz.kapp.user.service;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.user.client.CredentialStatusClient;
import co.edu.konradlorenz.kapp.user.client.CredentialStatusUpdate;
import co.edu.konradlorenz.kapp.user.domain.SearchTokens;
import co.edu.konradlorenz.kapp.user.domain.StoredInstant;
import co.edu.konradlorenz.kapp.user.domain.UserProfile;
import co.edu.konradlorenz.kapp.user.domain.UserRole;
import co.edu.konradlorenz.kapp.user.repository.UserDirectoryRepository;
import co.edu.konradlorenz.kapp.user.repository.UserProfileRepository;
import co.edu.konradlorenz.kapp.user.sinu.SinuStudentPort;
import co.edu.konradlorenz.kapp.user.web.dto.AcademicInfoResponse;
import co.edu.konradlorenz.kapp.user.web.dto.DirectoryEntry;
import co.edu.konradlorenz.kapp.user.web.dto.InternalUserUpsertRequest;
import co.edu.konradlorenz.kapp.user.web.dto.PageResponse;
import co.edu.konradlorenz.kapp.user.web.dto.ProfilePatch;
import co.edu.konradlorenz.kapp.user.web.dto.UserProfileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the service knows how to do to a profile.
 *
 * <p>Authorisation is not here. Who may reach which profile is decided at the edge, by
 * the security chain and {@code @PreAuthorize} on the controllers, and every method below
 * is handed the id it is meant to act on. That keeps one rule in one place instead of
 * splitting it across two layers that can disagree.
 */
@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private final UserProfileRepository repository;
    private final UserDirectoryRepository directory;
    private final CredentialStatusClient credentials;
    private final SinuStudentPort sinu;

    public UserProfileService(UserProfileRepository repository,
                              UserDirectoryRepository directory,
                              CredentialStatusClient credentials,
                              SinuStudentPort sinu) {
        this.repository = repository;
        this.directory = directory;
        this.credentials = credentials;
        this.sinu = sinu;
    }

    /** The caller's own profile, with their program, pensum and level when they are a student. */
    public UserProfileResponse me(String userId) {
        return withAcademic(load(userId));
    }

    /** Sets or clears the caller's picture, the one thing a person changes about their profile. */
    public UserProfileResponse update(String userId, ProfilePatch patch) {
        UserProfile updated = load(userId).withAvatarUrl(patch.avatarUrl(), StoredInstant.now());
        return withAcademic(repository.save(updated));
    }

    /** One account of the directory, for an administrator: nothing academic. */
    public DirectoryEntry entry(String userId) {
        return DirectoryEntry.from(load(userId));
    }

    /**
     * One page of the directory, newest account first.
     *
     * @param q free text, already length-checked by the caller. A query that folds down
     *          to nothing searchable matches nothing, rather than quietly turning into
     *          "no filter" and listing the whole directory
     */
    public PageResponse search(UserRole role, Boolean active, String q, int page, int size) {
        List<String> terms = SearchTokens.forQuery(q);
        if (q != null && terms.isEmpty()) {
            return PageResponse.of(List.of(), page, size, 0);
        }

        long total = directory.count(role, active, terms);
        List<DirectoryEntry> content = directory.findPage(role, active, terms, page, size)
                .stream()
                .map(DirectoryEntry::from)
                .toList();

        return PageResponse.of(content, page, size, total);
    }

    /**
     * Flips the activation flag, here and in auth-service.
     *
     * <p>Two facts in two databases: whether the profile is listed as active, which this service
     * owns, and whether the person may sign in, which auth-service owns. Only the first used to
     * happen - so "Deactivate" removed somebody from a filter and left them able to log in, with
     * the portal's own copy saying it was "the reversible way to take access away".
     *
     * <p>The credential goes first, and deliberately. If the profile write then fails, the
     * account is already locked out and a retry finishes the job; the other order would leave a
     * profile marked inactive whose owner could still sign in, which is the state this fixed.
     * Nothing here is a transaction - they are different databases - so the order is the only
     * safety there is.
     *
     * <p>Idempotent: setting the value the account already holds writes nothing on either side.
     */
    public DirectoryEntry setActive(String userId, boolean active) {
        UserProfile current = load(userId);

        credentials.setStatus(userId, new CredentialStatusUpdate(active));

        UserProfile updated = current.withActive(active, StoredInstant.now());
        return DirectoryEntry.from(updated == current ? current : repository.save(updated));
    }

    /**
     * The profile of an account an administrator deleted. Nothing else here points at a
     * profile, so removing the document removes the person from the directory.
     */
    public void deleteForAccount(String userId) {
        repository.deleteById(userId);
    }

    /**
     * Creates or updates the profile of an account auth-service has just written or signed in:
     * the names it was given and the roles it holds. Keyed by e-mail, so a retry after a network
     * timeout updates the profile the first attempt created instead of duplicating it - which is
     * why this endpoint returns 200 for both cases and never 409.
     *
     * @throws BusinessRuleException (400) unless the roles hold exactly one profile role, and no
     *                               {@code ROLE_GUEST}: a visitor holds a day pass, not an account
     */
    public DirectoryEntry upsertFromAuth(InternalUserUpsertRequest request) {
        List<UserRole> roles = checkRoles(request.roles());

        // Lowercased on the way in, and auth-service does the same on its side. Without
        // it a retry that differs only in capitalisation slips past the unique index and
        // creates exactly the duplicate this upsert exists to prevent.
        String email = request.email().strip().toLowerCase(Locale.ROOT);

        try {
            return DirectoryEntry.from(upsertOnce(email, request, roles));
        } catch (DuplicateKeyException race) {
            // Two sign-ins for the same address at once: both found nothing and both
            // inserted. The unique index rejected this one, so the other has landed and
            // the retry finds it and updates instead.
            log.info("Concurrent profile creation for an e-mail already taken; retrying as an update");
            return DirectoryEntry.from(upsertOnce(email, request, roles));
        }
    }

    private UserProfile upsertOnce(String email, InternalUserUpsertRequest request, List<UserRole> roles) {
        Instant now = StoredInstant.now();
        Optional<UserProfile> existing = repository.findByEmail(email);

        UserProfile profile = existing
                .map(current -> new UserProfile(
                        current.id(),
                        email,
                        request.firstName(),
                        request.lastName(),
                        // The picture is the person's own: a sign-in must not wipe it.
                        current.avatarUrl(),
                        roles,
                        current.active(),
                        List.of(),
                        current.createdAt(),
                        now))
                .orElseGet(() -> new UserProfile(
                        UUID.randomUUID().toString(),
                        email,
                        request.firstName(),
                        request.lastName(),
                        null,
                        roles,
                        true,
                        List.of(),
                        now,
                        now));

        return repository.save(profile);
    }

    private static List<UserRole> checkRoles(List<UserRole> requested) {
        List<UserRole> roles = requested.stream().distinct().toList();
        if (roles.contains(UserRole.ROLE_GUEST)) {
            throw new BusinessRuleException("A visitor holds a day pass, not an account",
                    List.of(new ApiError.FieldIssue("roles", "must not hold " + UserRole.ROLE_GUEST)));
        }
        long profileRoles = roles.stream().filter(UserRole::isProfileRole).count();
        if (profileRoles != 1) {
            throw new BusinessRuleException("An account holds exactly one profile role",
                    List.of(new ApiError.FieldIssue("roles",
                            "must hold exactly one of ROLE_STUDENT, ROLE_PROFESSOR or ROLE_STAFF")));
        }
        return roles;
    }

    /**
     * The profile with the academic block a student's carries, read from SINU now. With SINU out of
     * reach the profile is still served, the block null: a person's name and picture do not depend
     * on the university's record.
     */
    private UserProfileResponse withAcademic(UserProfile profile) {
        if (!profile.hasRole(UserRole.ROLE_STUDENT)) {
            return UserProfileResponse.from(profile, null);
        }
        AcademicInfoResponse academic;
        try {
            academic = sinu.student(profile.id(), profile.email()).map(AcademicInfoResponse::from).orElse(null);
        } catch (RuntimeException e) {
            log.warn("Could not read the academic block of {} from SINU; serving the profile without it",
                    profile.id(), e);
            academic = null;
        }
        return UserProfileResponse.from(profile, academic);
    }

    private UserProfile load(String userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User profile", userId));
    }
}

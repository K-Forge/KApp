package co.edu.konradlorenz.kapp.user.service;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.user.domain.AcademicInfo;
import co.edu.konradlorenz.kapp.user.domain.SearchTokens;
import co.edu.konradlorenz.kapp.user.domain.StoredInstant;
import co.edu.konradlorenz.kapp.user.client.CredentialStatusClient;
import co.edu.konradlorenz.kapp.user.client.CredentialStatusUpdate;
import co.edu.konradlorenz.kapp.user.domain.UserProfile;
import co.edu.konradlorenz.kapp.user.domain.UserRole;
import co.edu.konradlorenz.kapp.user.repository.UserDirectoryRepository;
import co.edu.konradlorenz.kapp.user.repository.UserProfileRepository;
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

    public UserProfileService(UserProfileRepository repository,
                              UserDirectoryRepository directory,
                              CredentialStatusClient credentials) {
        this.repository = repository;
        this.directory = directory;
        this.credentials = credentials;
    }

    /** The profile of the account a token belongs to, resolved from its {@code sub}. */
    public UserProfileResponse byId(String userId) {
        return UserProfileResponse.from(load(userId));
    }

    /**
     * Applies a partial update to one profile.
     *
     * <p>The identity, e-mail, role and activation flag are carried over from the stored
     * document rather than read from the patch: the reader has already rejected any
     * attempt to send them, and taking them from the document means a future field added
     * to the patch cannot reach them by accident.
     */
    public UserProfileResponse update(String userId, ProfilePatch patch) {
        UserProfile current = load(userId);

        if (patch.academic().hasValue() && current.role().isGuest()) {
            throw new BusinessRuleException(
                    "A guest account has no academic record",
                    List.of(new ApiError.FieldIssue("academic",
                            "must be null for a " + UserRole.ROLE_GUEST + " account")));
        }

        UserProfile updated = new UserProfile(
                current.id(),
                current.email(),
                patch.firstName().orElse(current.firstName()),
                patch.lastName().orElse(current.lastName()),
                patch.identification().orElse(current.identification()),
                patch.phone().orElse(current.phone()),
                patch.avatarUrl().orElse(current.avatarUrl()),
                current.role(),
                current.active(),
                patch.academic().orElse(current.academic()),
                // Derived: the compact constructor recomputes it from the names above.
                List.of(),
                current.createdAt(),
                StoredInstant.now());

        return UserProfileResponse.from(repository.save(updated));
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
        List<UserProfileResponse> content = directory.findPage(role, active, terms, page, size)
                .stream()
                .map(UserProfileResponse::from)
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
    public UserProfileResponse setActive(String userId, boolean active) {
        UserProfile current = load(userId);

        credentials.setStatus(userId, new CredentialStatusUpdate(active));

        UserProfile updated = current.withActive(active, StoredInstant.now());
        return UserProfileResponse.from(updated == current ? current : repository.save(updated));
    }

    /**
     * Creates or updates the profile that matches a credential auth-service has just
     * written. Keyed by e-mail, so a retry after a network timeout updates the profile the
     * first attempt created instead of duplicating it - which is why this endpoint returns
     * 200 for both cases and never 409.
     */
    /**
     * The profile of an account an administrator deleted. Nothing else here points at a
     * profile, so removing the document removes the person from the directory.
     */
    public void deleteForAccount(String userId) {
        repository.deleteById(userId);
    }

    public UserProfileResponse upsertFromRegistration(InternalUserUpsertRequest request) {
        requireAcademicToMatchRole(request.role(), request.academic());

        // Lowercased on the way in, and auth-service does the same on its side. Without
        // it a retry that differs only in capitalisation slips past the unique index and
        // creates exactly the duplicate this upsert exists to prevent.
        String email = request.email().strip().toLowerCase(Locale.ROOT);

        try {
            return UserProfileResponse.from(upsertOnce(email, request));
        } catch (DuplicateKeyException race) {
            // Two registrations for the same address at once: both found nothing and both
            // inserted. The unique index rejected this one, so the other has landed and
            // the retry finds it and updates instead.
            log.info("Concurrent profile creation for an e-mail already taken; retrying as an update");
            return UserProfileResponse.from(upsertOnce(email, request));
        }
    }

    private UserProfile upsertOnce(String email, InternalUserUpsertRequest request) {
        Instant now = StoredInstant.now();
        Optional<UserProfile> existing = repository.findByEmail(email);

        UserProfile profile = existing
                .map(current -> new UserProfile(
                        current.id(),
                        email,
                        request.firstName(),
                        request.lastName(),
                        // Contact details belong to the user, not to the registrar: a
                        // replayed registration must not wipe a phone number they added.
                        current.identification(),
                        current.phone(),
                        current.avatarUrl(),
                        request.role(),
                        current.active(),
                        request.academic(),
                        List.of(),
                        current.createdAt(),
                        now))
                .orElseGet(() -> new UserProfile(
                        UUID.randomUUID().toString(),
                        email,
                        request.firstName(),
                        request.lastName(),
                        null,
                        null,
                        null,
                        request.role(),
                        true,
                        request.academic(),
                        List.of(),
                        now,
                        now));

        return repository.save(profile);
    }

    private void requireAcademicToMatchRole(UserRole role, AcademicInfo academic) {
        if (role.isGuest() && academic != null) {
            throw new BusinessRuleException(
                    "A guest account has no academic record",
                    List.of(new ApiError.FieldIssue("academic",
                            "must be null for a " + UserRole.ROLE_GUEST + " account")));
        }
        if (role == UserRole.ROLE_STUDENT && academic == null) {
            throw new BusinessRuleException(
                    "A student account requires an academic record",
                    List.of(new ApiError.FieldIssue("academic",
                            "must be present for a " + UserRole.ROLE_STUDENT + " account")));
        }
    }

    private UserProfile load(String userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User profile", userId));
    }
}

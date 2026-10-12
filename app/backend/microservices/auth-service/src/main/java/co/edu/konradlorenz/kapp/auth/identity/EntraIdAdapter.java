package co.edu.konradlorenz.kapp.auth.identity;

import co.edu.konradlorenz.kapp.auth.client.InternalUserUpsert;
import co.edu.konradlorenz.kapp.auth.client.UserProfileClient;
import co.edu.konradlorenz.kapp.auth.client.UserProfileView;
import co.edu.konradlorenz.kapp.auth.domain.Credential;
import co.edu.konradlorenz.kapp.auth.domain.CredentialRepository;
import co.edu.konradlorenz.kapp.auth.domain.RefreshToken;
import co.edu.konradlorenz.kapp.auth.error.AccountDeactivatedException;
import co.edu.konradlorenz.kapp.auth.error.PortalPermissionRequiredException;
import co.edu.konradlorenz.kapp.auth.error.ProfileServiceUnavailableException;
import co.edu.konradlorenz.kapp.auth.identity.MicrosoftIdentity.DirectoryProfile;
import co.edu.konradlorenz.kapp.common.security.KappRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Signing in with Microsoft: the ID token a client brings is checked, and the account it names is
 * found, or created on the first sign-in, and brought up to date.
 *
 * <ul>
 *   <li><strong>Which account.</strong> By the person's id at Microsoft ({@code oid}), then by
 *       address, so an account created locally before - in development - is adopted rather than
 *       duplicated. The tenant owns its addresses: a recreated person with a new {@code oid} keeps
 *       the account of their address.</li>
 *   <li><strong>The profile role</strong> is the directory's at every sign-in - a student who
 *       starts teaching changes with it - unless an administrator chose one, which then stays. An
 *       account the directory does not classify is {@code ROLE_STAFF}: the map and nothing
 *       academic.</li>
 *   <li><strong>Permissions</strong> are never the directory's. They stay as an administrator left
 *       them, and the first administrators come from configuration, so somebody can open the portal
 *       on a new server.</li>
 *   <li><strong>The profile</strong> in user-service gets the names Microsoft gives and the roles at
 *       every sign-in.</li>
 *   <li><strong>The portal</strong> takes only an account with a permission.</li>
 * </ul>
 */
@Component
public class EntraIdAdapter implements IdentityProviderPort {

    private static final Logger log = LoggerFactory.getLogger(EntraIdAdapter.class);

    public static final String PROVIDER_ID = "entra-id";
    private static final int NAME_MAX = 50;

    private final MicrosoftIdTokenVerifier verifier;
    private final CredentialRepository credentials;
    private final UserProfileClient userProfiles;
    private final Set<String> firstAdmins;

    public EntraIdAdapter(MicrosoftIdTokenVerifier verifier, CredentialRepository credentials,
                          UserProfileClient userProfiles,
                          @Value("${kapp.auth.first-admins:}") List<String> firstAdmins) {
        this.verifier = verifier;
        this.credentials = credentials;
        this.userProfiles = userProfiles;
        this.firstAdmins = firstAdmins.stream()
                .map(email -> email.strip().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean supports(IdentityAssertion assertion) {
        return assertion instanceof IdentityAssertion.MicrosoftIdToken;
    }

    @Override
    public AuthenticatedIdentity authenticate(IdentityAssertion assertion) {
        if (!(assertion instanceof IdentityAssertion.MicrosoftIdToken token)) {
            throw new IllegalArgumentException("EntraIdAdapter only handles Microsoft ID tokens");
        }
        MicrosoftIdentity person = verifier.verify(token.idToken());

        Optional<Credential> existing = credentials.findByExternalId(person.objectId())
                .or(() -> credentials.findByEmailIgnoreCase(person.email()));
        if (existing.isPresent() && existing.get().status() == Credential.Status.SUSPENDED) {
            log.info("Sign-in with Microsoft refused for {}: the account is deactivated", person.email());
            throw new AccountDeactivatedException();
        }

        List<String> roles = rolesFor(person, existing);
        UserProfileView profile = upsertProfile(person, roles);
        Credential account = save(person, existing, roles, profile);

        if (token.client() == RefreshToken.Client.PORTAL
                && account.roles().stream().noneMatch(KappRoles.PERMISSIONS::contains)) {
            log.info("Portal sign-in refused for {}: the account holds no permission", account.email());
            throw new PortalPermissionRequiredException();
        }
        return new AuthenticatedIdentity(account.userId(), account.email(), account.roles(), true);
    }

    /** The profile role first, then the permissions, in their fixed order. */
    private List<String> rolesFor(MicrosoftIdentity person, Optional<Credential> existing) {
        String profileRole = existing
                .filter(Credential::profileRoleIsAdministrators)
                .flatMap(account -> account.roles().stream().filter(KappRoles.PROFILE_ROLES::contains).findFirst())
                .orElseGet(() -> person.profile().map(EntraIdAdapter::roleOf).orElse(KappRoles.STAFF));

        Set<String> permissions = existing.map(account -> account.roles().stream()
                        .filter(KappRoles.PERMISSIONS::contains)
                        .collect(Collectors.toCollection(HashSet::new)))
                .orElseGet(HashSet::new);
        if (firstAdmins.contains(person.email()) && permissions.add(KappRoles.ADMIN)) {
            log.info("{} is one of the first administrators: ROLE_ADMIN granted", person.email());
        }

        List<String> roles = new ArrayList<>();
        roles.add(profileRole);
        KappRoles.PERMISSIONS.stream().filter(permissions::contains).forEach(roles::add);
        return roles;
    }

    private static String roleOf(DirectoryProfile profile) {
        return switch (profile) {
            case STUDENT -> KappRoles.STUDENT;
            case PROFESSOR -> KappRoles.PROFESSOR;
            case STAFF -> KappRoles.STAFF;
        };
    }

    private UserProfileView upsertProfile(MicrosoftIdentity person, List<String> roles) {
        String[] names = names(person);
        try {
            UserProfileView profile = userProfiles.upsert(new InternalUserUpsert(person.email(), names[0], names[1], roles));
            if (profile == null || profile.id() == null || profile.id().isBlank()) {
                throw new IllegalStateException("user-service returned no profile id");
            }
            return profile;
        } catch (RuntimeException e) {
            throw new ProfileServiceUnavailableException(
                    "Signing in is temporarily unavailable. Please try again in a moment.", e);
        }
    }

    private Credential save(MicrosoftIdentity person, Optional<Credential> existing, List<String> roles,
                            UserProfileView profile) {
        Instant now = Instant.now();
        if (existing.isPresent()) {
            Credential account = existing.get();
            if (!account.userId().equals(profile.id())) {
                log.warn("Account {} and its profile {} disagree on the id; the account's is kept",
                        account.userId(), profile.id());
            }
            Credential updated = account;
            if (!person.objectId().equals(account.externalId())) {
                updated = updated.withExternalId(person.objectId(), now);
            }
            if (!roles.equals(account.roles())) {
                updated = updated.withRoles(roles, false, now);
            }
            return updated == account ? account : credentials.save(updated);
        }
        try {
            Credential created = credentials.save(Credential.newMicrosoftAccount(profile.id(), person.email(), roles,
                    person.objectId(), now));
            log.info("Account {} created at its first sign-in with Microsoft", created.userId());
            return created;
        } catch (DuplicateKeyException race) {
            // Two first sign-ins at once: the other one created it, and it is this person's.
            return credentials.findByExternalId(person.objectId()).orElseThrow(() -> race);
        }
    }

    /**
     * First and last name, as user-service requires both: Microsoft's given and family names, or
     * else the display name split at its first space, or else the address's local part.
     */
    static String[] names(MicrosoftIdentity person) {
        String first = person.givenName();
        String last = person.familyName();
        if (first == null || last == null) {
            String whole = person.displayName() != null ? person.displayName()
                    : person.email().substring(0, person.email().indexOf('@')).replace('.', ' ');
            String[] parts = whole.strip().split("\\s+", 2);
            first = first != null ? first : parts[0];
            last = last != null ? last : parts.length > 1 ? parts[1] : parts[0];
        }
        return new String[]{cut(first), cut(last)};
    }

    private static String cut(String name) {
        String stripped = name.strip();
        return stripped.length() <= NAME_MAX ? stripped : stripped.substring(0, NAME_MAX);
    }
}

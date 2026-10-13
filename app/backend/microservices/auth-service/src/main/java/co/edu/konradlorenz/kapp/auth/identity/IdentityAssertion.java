package co.edu.konradlorenz.kapp.auth.identity;

/**
 * What a caller presents in order to prove who they are.
 *
 * <p>Sealed on purpose. The set of ways to authenticate against KApp is small and known,
 * and a sealed hierarchy makes an exhaustive {@code switch} in a provider a compile-time
 * obligation rather than a runtime surprise when a new assertion type appears.
 *
 * <p>{@link MicrosoftIdToken} is how members of the university sign in. {@link Password} is for
 * development, CI and the mocks, behind the local sign-in flag.
 */
public sealed interface IdentityAssertion
        permits IdentityAssertion.Password, IdentityAssertion.MicrosoftIdToken {

    /**
     * E-mail and password, verified against a locally stored BCrypt hash.
     *
     * @param email        the login name, in whatever case the user typed it
     * @param rawPassword  the plain-text password, never logged and never stored
     * @param allowedRoles the roles the client takes, any one of them; empty for any account
     */
    record Password(String email, String rawPassword, java.util.List<String> allowedRoles) implements IdentityAssertion {
        public Password {
            allowedRoles = allowedRoles == null ? java.util.List.of() : java.util.List.copyOf(allowedRoles);
        }
    }

    /**
     * The ID token a client brought back from Microsoft's sign-in, with which client it is.
     *
     * @param idToken as Microsoft issued it, never trusted before {@code MicrosoftIdTokenVerifier}
     *                checked it
     * @param client  the apps or the portal: the portal takes only an account with a permission
     */
    record MicrosoftIdToken(String idToken, co.edu.konradlorenz.kapp.auth.domain.RefreshToken.Client client)
            implements IdentityAssertion {
    }
}

package co.edu.konradlorenz.kapp.auth.identity;

/**
 * What a caller presents in order to prove who they are.
 *
 * <p>Sealed on purpose. The set of ways to authenticate against KApp is small and known,
 * and a sealed hierarchy makes an exhaustive {@code switch} in a provider a compile-time
 * obligation rather than a runtime surprise when a new assertion type appears.
 *
 * <p>{@link AuthorizationCode} is deliberately unimplemented. It exists so the shape of
 * {@link IdentityProviderPort} is proven against the flow Microsoft Entra ID will
 * actually use, instead of being designed around passwords and then bent later. Nothing
 * supports it today, so resolving a provider for one fails loudly.
 */
public sealed interface IdentityAssertion
        permits IdentityAssertion.Password, IdentityAssertion.AuthorizationCode {

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
     * An OAuth2 / OIDC authorization code obtained from an external identity provider.
     *
     * <p>Not implemented in the MVP: the university has not granted an application
     * registration yet. Present because it is the assertion an Entra adapter receives,
     * and the port has to be able to carry it without changing shape.
     *
     * @param code         the authorization code returned to the client's redirect URI
     * @param redirectUri  the redirect URI the code was issued for
     * @param codeVerifier the PKCE verifier matching the challenge sent with the request
     */
    record AuthorizationCode(String code, String redirectUri, String codeVerifier)
            implements IdentityAssertion {
    }
}

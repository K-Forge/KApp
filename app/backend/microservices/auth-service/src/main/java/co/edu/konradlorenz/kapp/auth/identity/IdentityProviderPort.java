package co.edu.konradlorenz.kapp.auth.identity;

/**
 * The seam between "who is this person" and everything else KApp does.
 *
 * <p>Three methods, and none of them mentions BCrypt, MongoDB, or account creation. That
 * is the whole design: {@code AuthService} depends on this interface, which is how
 * {@code EntraIdAdapter} came to sit beside {@code LocalIdentityProvider} with no caller changing. Widening this signature - a
 * {@code createAccount}, a {@code Credential} return type, a password-hash parameter -
 * would quietly re-couple authentication to local storage and undo that.
 *
 * <p>Implementations are Spring beans; {@code AuthService} holds all of them and asks each
 * whether it {@link #supports(IdentityAssertion)} the assertion at hand.
 */
public interface IdentityProviderPort {

    /** Stable identifier for this provider, e.g. {@code local} or {@code entra-id}. */
    String providerId();

    /** Whether this provider can evaluate the given assertion at all. */
    boolean supports(IdentityAssertion assertion);

    /**
     * Verifies the assertion.
     *
     * @return the identity behind it
     * @throws co.edu.konradlorenz.kapp.common.error.InvalidCredentialsException
     *         when the assertion does not hold, or when the account may not sign in for
     *         a reason the caller must not be able to distinguish
     * @throws org.springframework.security.access.AccessDeniedException
     *         when the credentials are right but the account is not cleared to sign in
     *         yet, which the contract renders as HTTP 403
     */
    AuthenticatedIdentity authenticate(IdentityAssertion assertion);
}

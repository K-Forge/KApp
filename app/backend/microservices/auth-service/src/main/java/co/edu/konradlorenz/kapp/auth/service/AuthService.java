package co.edu.konradlorenz.kapp.auth.service;

import co.edu.konradlorenz.kapp.auth.identity.AuthenticatedIdentity;
import co.edu.konradlorenz.kapp.auth.identity.IdentityAssertion;
import co.edu.konradlorenz.kapp.auth.identity.IdentityProviderPort;
import co.edu.konradlorenz.kapp.auth.jwt.JwtIssuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Turns a verified identity into an access token.
 *
 * <p>Deliberately thin, and deliberately ignorant. It does not know that BCrypt exists,
 * that credentials live in MongoDB, or that an account has a status: it hands an
 * assertion to whichever {@link IdentityProviderPort} claims it and signs a token for
 * whatever identity comes back. That is what makes the Entra ID migration a matter of
 * adding a bean rather than rewriting sign-in.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final List<IdentityProviderPort> providers;
    private final JwtIssuer jwtIssuer;

    public AuthService(List<IdentityProviderPort> providers, JwtIssuer jwtIssuer) {
        this.providers = providers;
        this.jwtIssuer = jwtIssuer;
    }

    public JwtIssuer.IssuedToken login(String email, String rawPassword, List<String> allowedRoles) {
        return authenticate(new IdentityAssertion.Password(email, rawPassword, allowedRoles));
    }

    public JwtIssuer.IssuedToken authenticate(IdentityAssertion assertion) {
        IdentityProviderPort provider = providerFor(assertion);
        AuthenticatedIdentity identity = provider.authenticate(assertion);

        log.info("Issued token for user {} via provider {}", identity.subject(), provider.providerId());
        return jwtIssuer.issue(identity.subject(), identity.email(), identity.roles());
    }

    /**
     * @throws IllegalStateException when nothing can evaluate the assertion. That is a
     *         wiring mistake rather than a client error - today it is what an
     *         {@link IdentityAssertion.AuthorizationCode} gets, because the Entra adapter
     *         does not exist yet.
     */
    private IdentityProviderPort providerFor(IdentityAssertion assertion) {
        return providers.stream()
                .filter(p -> p.supports(assertion))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No identity provider supports " + assertion.getClass().getSimpleName()));
    }
}

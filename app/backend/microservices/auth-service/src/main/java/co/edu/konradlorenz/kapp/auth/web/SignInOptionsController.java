package co.edu.konradlorenz.kapp.auth.web;

import co.edu.konradlorenz.kapp.auth.config.MicrosoftProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * How to sign in to this server, for a client that has not signed in yet: the admin portal draws its
 * sign-in page from it, so one build of the portal works against any server.
 *
 * <p>Public, and nothing in it is a secret: an authority and a client id are what a browser shows in
 * its address bar during any sign-in.
 */
@RestController
@Tag(name = "Authentication")
public class SignInOptionsController {

    private final MicrosoftSignInOptions microsoft;
    private final boolean localSignIn;

    public SignInOptionsController(MicrosoftProperties properties,
                                   @Value("${kapp.auth.microsoft.portal-client-id:}") String portalClientId,
                                   @Value("${kapp.auth.local-sign-in.enabled:false}") boolean localSignIn) {
        this.microsoft = properties.configured()
                ? new MicrosoftSignInOptions(properties.issuers().getFirst(),
                        portalClientId.isBlank() ? properties.clientIds().getFirst() : portalClientId.strip())
                : null;
        this.localSignIn = localSignIn;
    }

    @GetMapping("/auth/sign-in-options")
    @Operation(summary = "How to sign in to this server",
            description = "Where Microsoft's sign-in is, and whether password sign-in is open. Public.")
    public SignInOptions signInOptions() {
        return new SignInOptions(microsoft, localSignIn);
    }

    /** {@code microsoft} is written as null, never left out, when Microsoft is not configured here. */
    public record SignInOptions(MicrosoftSignInOptions microsoft, boolean localSignIn) {
    }

    /**
     * @param authority the issuer of the ID tokens this server accepts; its discovery document is at
     *                  {@code <authority>/.well-known/openid-configuration}
     * @param clientId  the application id the admin portal signs in as
     */
    public record MicrosoftSignInOptions(String authority, String clientId) {
    }
}

package co.edu.konradlorenz.kapp.auth.web;

import co.edu.konradlorenz.kapp.auth.service.AuthService;
import co.edu.konradlorenz.kapp.auth.service.PasswordService;
import co.edu.konradlorenz.kapp.auth.service.RegistrationService;
import co.edu.konradlorenz.kapp.auth.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Public authentication endpoints, implementing {@code docs/api/auth.openapi.yaml}.
 *
 * <p>Every method here is reachable without a token, and every one of them is listed in
 * {@code AuthSecurityConfig.PUBLIC_AUTH_PATHS}. Those two facts have to stay in step:
 * anything added under {@code /auth/**} and not listed there requires a token, which is
 * the safe direction for the mistake to go.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final VerificationService verificationService;
    private final PasswordService passwordService;

    public AuthController(AuthService authService,
                          RegistrationService registrationService,
                          VerificationService verificationService,
                          PasswordService passwordService) {
        this.authService = authService;
        this.registrationService = registrationService;
        this.verificationService = verificationService;
        this.passwordService = passwordService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register an institutional account",
            description = "The invitation code decides the role. ROLE_ADMIN is never granted here.")
    public RegistrationResponse register(@Valid @RequestBody RegistrationRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for an access token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        var issued = authService.login(request.email(), request.password(), request.allowedRoles());
        return new TokenResponse(
                issued.accessToken(), issued.tokenType(), issued.expiresIn(),
                issued.userId(), issued.roles());
    }

    /**
     * A new password for the current one, and signed in with it: how a temporary password an
     * administrator issued is replaced at first sign-in. Public, like login - the current
     * password is the proof - and rate-limited with it at the gateway.
     */
    @PostMapping("/password")
    @Operation(summary = "Replace a password, and sign in with the new one")
    public TokenResponse changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        var issued = passwordService.change(request.email(), request.currentPassword(), request.newPassword(),
                request.allowedRoles());
        return new TokenResponse(
                issued.accessToken(), issued.tokenType(), issued.expiresIn(),
                issued.userId(), issued.roles());
    }

    /**
     * 204 with no body. There is nothing useful to return: the client already knows which
     * address it was confirming, and echoing the account back would make a token that
     * leaked into a browser history worth replaying for the information alone.
     */
    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirm an e-mail address",
            description = "Single use. A replayed, expired or unknown token is a 400.")
    public void verify(@Valid @RequestBody VerificationRequest request) {
        verificationService.verify(request.token());
    }

    /**
     * Always 202, whatever happened. Existing address, unknown address, already confirmed:
     * one status and one message. Anything else would let a caller enumerate accounts.
     */
    @PostMapping("/verify/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a new verification e-mail",
            description = "Always 202, and always the same message, whether or not the account exists.")
    public AcceptedResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        verificationService.resend(request.email());
        return new AcceptedResponse(VerificationService.NEUTRAL_RESEND_MESSAGE);
    }

    @GetMapping("/health")
    @Operation(summary = "Liveness probe")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "auth-service");
    }

    /** The roles a client may name in {@code allowedRoles}. */
    static final String ROLE_PATTERN = "ROLE_(GUEST|STUDENT|PROFESSOR|ADMIN)";

    /**
     * {@code allowedRoles}: the roles the client takes, any one of them. The admin portal sends
     * {@code [ROLE_ADMIN]}, so a student's or a professor's valid password is refused there -
     * before a temporary one is offered for replacing. Absent or empty, any account.
     */
    public record LoginRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @Size(max = 4) List<@Pattern(regexp = ROLE_PATTERN) String> allowedRoles) {
    }

    public record PasswordChangeRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(min = 10, max = 72) String currentPassword,
            @NotBlank @Size(min = 10, max = 72) String newPassword,
            @Size(max = 4) List<@Pattern(regexp = ROLE_PATTERN) String> allowedRoles) {
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            String userId,
            List<String> roles) {
    }
}

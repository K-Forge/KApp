package co.edu.konradlorenz.kapp.auth.web;

import co.edu.konradlorenz.kapp.auth.service.PasswordService;
import co.edu.konradlorenz.kapp.auth.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Accounts an administrator creates, each with a temporary password the person replaces at
 * first sign-in - the way in until the university's own identity provider is: an
 * administrator vouches for somebody instead of an invitation code doing it.
 *
 * <p>{@code ROLE_ADMIN} on each handler, as on the invitation codes, for the same reason.
 */
@RestController
@RequestMapping("/auth/admin/accounts")
@Tag(name = "Accounts")
public class AdminAccountController {

    private final RegistrationService registration;
    private final PasswordService passwords;

    public AdminAccountController(RegistrationService registration, PasswordService passwords) {
        this.registration = registration;
        this.passwords = passwords;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account with a temporary password")
    public TemporaryPasswordResponse createAccount(@Valid @RequestBody AccountRequest body) {
        return TemporaryPasswordResponse.from(registration.createWithTemporaryPassword(body));
    }

    @PostMapping("/{userId}/temporary-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Give an account a new temporary password")
    public TemporaryPasswordResponse issueTemporaryPassword(@PathVariable String userId) {
        return TemporaryPasswordResponse.from(passwords.issueTemporary(userId));
    }
}

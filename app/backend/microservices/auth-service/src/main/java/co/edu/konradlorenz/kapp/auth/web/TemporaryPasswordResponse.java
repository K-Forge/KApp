package co.edu.konradlorenz.kapp.auth.web;

import co.edu.konradlorenz.kapp.auth.service.PasswordService;

import java.time.Instant;
import java.util.List;

/**
 * Mirrors {@code TemporaryPassword}: the one answer the temporary password appears in, since
 * only its hash is stored.
 */
public record TemporaryPasswordResponse(
        String userId,
        String email,
        List<String> roles,
        String temporaryPassword,
        Instant expiresAt) {

    public static TemporaryPasswordResponse from(PasswordService.Issued issued) {
        var credential = issued.credential();
        return new TemporaryPasswordResponse(credential.userId(), credential.email(),
                credential.roles(),
                issued.temporaryPassword(), issued.expiresAt());
    }
}

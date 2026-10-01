package co.edu.konradlorenz.kapp.auth.service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Temporary passwords: what an administrator hands over, by voice or by message, for one
 * sign-in that replaces it.
 *
 * <p>Three groups of four from the alphabet invitation codes use - no 0 or O, no 1 or I - so
 * it reads out loud without doubt: "K7QM-X2RP-94TB". Twelve characters from thirty-two are 60
 * bits, against a login the gateway allows ten tries a minute, for seven days at most.
 */
final class TemporaryPasswords {

    /** How long one is accepted. A week covers a weekend and somebody who did not open it at once. */
    static final Duration LIFETIME = Duration.ofDays(7);

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswords() {
    }

    static String next() {
        StringBuilder password = new StringBuilder(14);
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                password.append('-');
            }
            password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}

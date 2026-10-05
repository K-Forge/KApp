package co.edu.konradlorenz.kapp.auth.error;

/**
 * The password was right, and temporary: an administrator issued it, and it signs nobody in
 * until the person sets their own with {@code POST /auth/password}.
 *
 * <p>Rendered as 403 with an issue for the field {@code newPassword}, which is what the
 * contract tells clients to look for. The other 403 at login - an unconfirmed address - has
 * no details, so the two cannot be mistaken for each other.
 */
public class PasswordChangeRequiredException extends RuntimeException {

    public PasswordChangeRequiredException() {
        super("This password is temporary: choose a new one to sign in");
    }
}

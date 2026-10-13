package co.edu.konradlorenz.kapp.auth.error;

/**
 * A session renewed for an account an administrator deactivated: 403, so the client tells the
 * person their access was taken away rather than asking them to sign in again, which would fail
 * the same way.
 */
public class AccountDeactivatedException extends RuntimeException {

    public AccountDeactivatedException() {
        super("This account is deactivated");
    }
}

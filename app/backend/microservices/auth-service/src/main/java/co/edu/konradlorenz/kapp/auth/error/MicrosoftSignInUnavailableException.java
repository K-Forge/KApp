package co.edu.konradlorenz.kapp.auth.error;

/**
 * A Microsoft sign-in that could not be checked, through nobody's fault but the server's: Microsoft
 * is not configured here, or its keys could not be fetched. Not a 401 - the person's token may be
 * perfectly good, and trying again later may work.
 */
public class MicrosoftSignInUnavailableException extends RuntimeException {

    public MicrosoftSignInUnavailableException(String message) {
        super(message);
    }

    public MicrosoftSignInUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

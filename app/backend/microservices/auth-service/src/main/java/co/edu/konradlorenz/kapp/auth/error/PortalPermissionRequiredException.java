package co.edu.konradlorenz.kapp.auth.error;

/**
 * A sign-in to the admin portal by an account with no permission: 403 with an issue for the field
 * {@code client}, and nothing issued. The same person signs in to the app as usual.
 */
public class PortalPermissionRequiredException extends RuntimeException {

    public PortalPermissionRequiredException() {
        super("This account has no permission for the admin portal");
    }
}

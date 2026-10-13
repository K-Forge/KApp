package co.edu.konradlorenz.kapp.auth.error;

/**
 * The e-mail and password were right, and the account is not one this client takes: the admin
 * portal asks for administrators only, and a student or a professor signs in in the app.
 *
 * <p>403 with an issue for the field {@code allowedRoles}, raised before anything else is done
 * with the account - in particular before a temporary password is offered for replacing, so a
 * client for one kind of account never changes another kind's password. It speaks only to
 * someone who holds the password, as the other two 403s at login do.
 */
public class RoleNotAllowedException extends RuntimeException {

    public RoleNotAllowedException() {
        super("This account cannot sign in here");
    }
}

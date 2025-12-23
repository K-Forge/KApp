package co.edu.konradlorenz.kapp.common.exception;

/**
 * Excepción para acceso denegado a recursos.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}

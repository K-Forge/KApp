package co.edu.konradlorenz.kapp.auth.error;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * The one failure mode {@link GlobalExceptionHandler} in {@code common} has no opinion
 * about: a downstream service being unreachable.
 *
 * <p>Ordered ahead of the shared advice, which is required rather than tidy. Spring picks
 * the first advice bean that can handle an exception, and the shared one ends with a
 * catch-all {@code @ExceptionHandler(Exception.class)} - so without an explicit order this
 * would lose the race and every dependency outage would surface as a 500.
 *
 * <p>It answers in the same {@link ApiError} envelope as everything else; the point is the
 * status code, not a second error format.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

    @ExceptionHandler(ProfileServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleProfileServiceDown(
            ProfileServiceUnavailableException ex, HttpServletRequest request) {

        log.error("Registration could not reach user-service", ex);

        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), ex.getMessage(),
                request.getRequestURI(), List.of());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(RoleNotAllowedException.class)
    public ResponseEntity<ApiError> handleRoleNotAllowed(RoleNotAllowedException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), ex.getMessage(),
                request.getRequestURI(),
                List.of(new ApiError.FieldIssue("allowedRoles", "The account has none of these roles. Nothing was changed")));
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(AccountDeactivatedException.class)
    public ResponseEntity<ApiError> handleAccountDeactivated(AccountDeactivatedException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), ex.getMessage(),
                request.getRequestURI(), List.of());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(PasswordChangeRequiredException.class)
    public ResponseEntity<ApiError> handlePasswordChangeRequired(
            PasswordChangeRequiredException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.FORBIDDEN;
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), ex.getMessage(),
                request.getRequestURI(),
                List.of(new ApiError.FieldIssue("newPassword",
                        "Required: the password is temporary. Send it with a new one to POST /auth/password")));
        return ResponseEntity.status(status).body(body);
    }
}

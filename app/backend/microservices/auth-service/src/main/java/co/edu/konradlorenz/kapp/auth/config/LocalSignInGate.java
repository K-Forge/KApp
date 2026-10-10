package co.edu.konradlorenz.kapp.auth.config;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Closes password sign-in and everything around it: registration, e-mail verification, invitation
 * codes, and accounts an administrator creates with a temporary password. Members of the university
 * sign in with Microsoft; these exist for development, CI and the mocks.
 *
 * <p>With local sign-in off - the default, and what production runs - their paths answer
 * {@code 404} with the same body as a path that does not exist, so nothing tells them apart. The
 * gate runs before Spring Security, so an administrator's token does not open them either, and it
 * matches the decoded path, so an encoded letter does not slip past it.
 *
 * <p>{@link #DEVELOPMENT_ONLY_PATHS} are the operations {@code docs/api/auth.openapi.yaml} marks
 * {@code x-development-only}; a test reads the contract and keeps the two in step.
 */
public class LocalSignInGate extends OncePerRequestFilter {

    public static final List<String> DEVELOPMENT_ONLY_PATHS = List.of(
            "/auth/login",
            "/auth/password",
            "/auth/register",
            "/auth/verify",
            "/auth/verify/resend",
            "/auth/admin/invitation-codes",
            "/auth/admin/invitation-codes/*",
            "/auth/admin/accounts",
            "/auth/admin/accounts/*/temporary-password");

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final ObjectMapper objectMapper;

    public LocalSignInGate(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Whether a path is one local sign-in owns, compared decoded and without path parameters. */
    public static boolean isDevelopmentOnly(String path) {
        return DEVELOPMENT_ONLY_PATHS.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isDevelopmentOnly(UrlPathHelper.defaultInstance.getPathWithinApplication(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // Word for word what common's handler answers for a path nothing serves.
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(), "No endpoint matches this path", request.getRequestURI(),
                List.of()));
    }
}

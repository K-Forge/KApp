package co.edu.konradlorenz.kapp.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Where Microsoft ID tokens come from, and how to read a person's profile role from one.
 *
 * <p>Nothing here is a secret: KApp exchanges the ID token a client brings, so it needs no client
 * secret. With {@code tenantId} set, the issuer and the key set are Microsoft's for that tenant.
 * Development points {@code issuers} and {@code jwksUri} at the fake OIDC server instead.
 *
 * @param tenantId        the directory people sign in to: the university's, or the test tenant
 * @param issuers         the accepted {@code iss}; by default the tenant's v2.0 issuer. Only
 *                        development lists more than one, since an emulator reaches the fake server
 *                        under another address
 * @param jwksUri         the keys ID tokens are signed with; by default the tenant's
 * @param clientIds       the application ids KApp's clients sign in with: an ID token's {@code aud}
 *                        must be one of them
 * @param profileClaim    the claim that carries the profile, as agreed with the university's IT
 *                        office: app roles by default
 * @param studentValues   values of that claim that mean a student, compared ignoring case
 * @param professorValues values that mean a professor
 * @param staffValues     values that mean an administrative employee
 */
@ConfigurationProperties(prefix = "kapp.auth.microsoft")
public record MicrosoftProperties(
        String tenantId,
        List<String> issuers,
        String jwksUri,
        List<String> clientIds,
        String profileClaim,
        List<String> studentValues,
        List<String> professorValues,
        List<String> staffValues
) {

    public MicrosoftProperties {
        tenantId = blankToNull(tenantId);
        issuers = clean(issuers);
        if (issuers.isEmpty() && tenantId != null) {
            issuers = List.of("https://login.microsoftonline.com/" + tenantId + "/v2.0");
        }
        jwksUri = blankToNull(jwksUri);
        if (jwksUri == null && tenantId != null) {
            jwksUri = "https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys";
        }
        clientIds = clean(clientIds);
        profileClaim = blankToNull(profileClaim) == null ? "roles" : profileClaim.strip();
        studentValues = lower(studentValues, "Student");
        professorValues = lower(professorValues, "Professor");
        staffValues = lower(staffValues, "Staff");
    }

    /** Whether a Microsoft sign-in can be verified at all on this server. */
    public boolean configured() {
        return !issuers.isEmpty() && jwksUri != null && !clientIds.isEmpty();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of()
                : values.stream().filter(Objects::nonNull).map(String::strip).filter(v -> !v.isEmpty()).toList();
    }

    private static List<String> lower(List<String> values, String fallback) {
        List<String> cleaned = clean(values);
        return (cleaned.isEmpty() ? List.of(fallback) : cleaned).stream()
                .map(v -> v.toLowerCase(Locale.ROOT))
                .toList();
    }
}

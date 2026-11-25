package co.edu.konradlorenz.kapp.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Refuses to start auth-service when sign-in with Microsoft is pointed at a stand-in, such as the
 * fake OIDC server, while the database is on MongoDB Atlas.
 *
 * <p>The fake OIDC server signs anybody in as an invented {@code @kforge.dev} person, some of
 * them first administrators. It is for evaluation, with the {@code local-db} profile's own
 * database (ADR 0010). Against the team's Atlas cluster it would create those accounts where the
 * dev server and every laptop see them, as it did before #246. So the two cannot meet: on Atlas,
 * the issuers and the key set must be Microsoft's ({@code login.microsoftonline.com}), and the
 * team signs in to the test tenant.
 */
@Component
public class StandInSignInGuard {

    static final String MICROSOFT_HOST = "login.microsoftonline.com";

    public StandInSignInGuard(MicrosoftProperties microsoft, @Value("${spring.data.mongodb.uri:}") String mongoUri) {
        check(microsoft, mongoUri);
    }

    static void check(MicrosoftProperties microsoft, String mongoUri) {
        if (!isAtlas(mongoUri)) {
            return;
        }
        List<String> standIns = new ArrayList<>();
        microsoft.issuers().stream().filter(issuer -> !isMicrosoft(issuer)).forEach(standIns::add);
        if (microsoft.jwksUri() != null && !isMicrosoft(microsoft.jwksUri())) {
            standIns.add(microsoft.jwksUri());
        }
        if (!standIns.isEmpty()) {
            throw new IllegalStateException("Sign-in with Microsoft points at " + standIns
                    + ", which is not Microsoft, while the database is on Atlas. The fake OIDC server is"
                    + " for the local-db profile only (ADR 0010, #246): set KAPP_MICROSOFT_TENANT_ID and"
                    + " KAPP_MICROSOFT_CLIENT_IDS for the test tenant, and leave KAPP_MICROSOFT_ISSUERS and"
                    + " KAPP_MICROSOFT_JWKS_URI empty or Microsoft's.");
        }
    }

    /** Atlas hands out {@code mongodb+srv://} addresses under {@code mongodb.net}. */
    static boolean isAtlas(String mongoUri) {
        if (mongoUri == null || mongoUri.isBlank()) {
            return false;
        }
        String uri = mongoUri.strip().toLowerCase(Locale.ROOT);
        if (uri.startsWith("mongodb+srv://")) {
            return true;
        }
        int at = uri.lastIndexOf('@');
        String hosts = uri.substring(at >= 0 ? at + 1 : uri.indexOf("://") + 3);
        int end = hosts.indexOf('/');
        hosts = end >= 0 ? hosts.substring(0, end) : hosts;
        for (String host : hosts.split(",")) {
            String name = host.contains(":") ? host.substring(0, host.indexOf(':')) : host;
            if (name.endsWith(".mongodb.net")) {
                return true;
            }
        }
        return false;
    }

    static boolean isMicrosoft(String address) {
        try {
            URI uri = URI.create(address.strip());
            return "https".equalsIgnoreCase(uri.getScheme()) && MICROSOFT_HOST.equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

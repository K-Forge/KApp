package co.edu.konradlorenz.kapp.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * The one Feign edge out of user-service: suspending the credential behind a profile.
 *
 * <p>Deactivating an account is two facts in two databases - the profile stops being listed as
 * active here, and the credential stops being allowed to sign in over there. Before this edge
 * existed only the first half happened, so "Deactivate" removed a person from a filter and left
 * them able to log in.
 *
 * <p>Addressed by {@code kapp.user.auth-service-url}: auth-service's name, which Docker Compose
 * and Kubernetes resolve, or whatever address a host or a test sets.
 *
 * <p>Authenticated with {@code X-Internal-Token}, not the administrator's bearer token: the
 * secret identifies a service, and {@code /internal/**} is not routed by the gateway.
 */
@FeignClient(
        name = "auth-service",
        url = "${kapp.user.auth-service-url:}",
        configuration = InternalCredentialClientConfig.class)
public interface CredentialStatusClient {

    /**
     * Idempotent. 204 on success; 404 when the two services disagree about who exists.
     *
     * <p>POST rather than PATCH because Feign's default client is
     * {@code java.net.HttpURLConnection}, which refuses PATCH outright -
     * {@code ProtocolException: Invalid HTTP method: PATCH}. Keeping the verb would mean
     * putting a different HTTP client under every Feign call in this service.
     */
    @PostMapping(value = "/internal/credentials/{userId}/status",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    void setStatus(@PathVariable("userId") String userId, @RequestBody CredentialStatusUpdate body);
}

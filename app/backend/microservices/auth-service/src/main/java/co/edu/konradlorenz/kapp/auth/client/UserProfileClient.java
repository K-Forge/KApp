package co.edu.konradlorenz.kapp.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * The one Feign edge out of auth-service: creating the profile that matches a new
 * credential, and removing it with the account.
 *
 * <p>Addressed by {@code kapp.auth.user-service-url}: user-service's name, which Docker Compose
 * and Kubernetes resolve, or whatever address a host or a test sets.
 *
 * <p>Authenticated with {@code X-Internal-Token}, not a bearer token, because at
 * registration time the account does not exist yet and there is no user token to forward.
 * See {@link InternalUserClientConfig}.
 */
@FeignClient(
        name = "user-service",
        url = "${kapp.auth.user-service-url:}",
        configuration = InternalUserClientConfig.class)
public interface UserProfileClient {

    /**
     * Idempotent upsert keyed by e-mail: a first call creates the profile, a repeated call
     * updates it. Always 200, never 201 and never 409, so replaying a registration after a
     * network timeout is safe.
     */
    @PostMapping(value = "/internal/users", consumes = MediaType.APPLICATION_JSON_VALUE)
    UserProfileView upsert(@RequestBody InternalUserUpsert body);

    /** The profile of an account an administrator deleted. Idempotent: 204 whether it was there or not. */
    @DeleteMapping("/internal/users/{id}")
    void delete(@PathVariable("id") String id);
}

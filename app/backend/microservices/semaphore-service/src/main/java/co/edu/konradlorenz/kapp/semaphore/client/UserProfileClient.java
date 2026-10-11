package co.edu.konradlorenz.kapp.semaphore.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The one call this service makes to another: resolving the caller's own
 * {@code programCode} so {@code GET /api/semaphore/me} can lazily create a semaforo.
 *
 * <p>Deliberately {@code GET /api/users/me}, not an admin lookup by id. This client is
 * only ever invoked while handling an authenticated student's own request, and
 * {@code common}'s {@code KappFeignAutoConfiguration} forwards that same caller's bearer
 * token onto this call - so user-service sees the request as coming from the student
 * itself, never from a semaphore-service credential, and can never be asked for anyone
 * else's profile through this seam.
 */
@FeignClient(name = "user-service", contextId = "userProfileClient",
        url = "${kapp.semaphore.user-service-url}", path = "/api/users")
public interface UserProfileClient {

    @GetMapping("/me")
    UserProfileResponse getMyProfile();
}

package co.edu.konradlorenz.kapp.semaphore.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Authorises {@code /api/semaphore/me/**}: {@code ROLE_STUDENT} only.
 *
 * <p>Narrower than {@link CatalogRead} on purpose. A professor or a staff member follows no
 * program, so has no semáforo and no plans to read - the published authorization table gives them
 * no reach here at all, not even read-only. An administrator has none either: no route reads
 * another student's semáforo.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('STUDENT')")
public @interface StudentOnly {
}

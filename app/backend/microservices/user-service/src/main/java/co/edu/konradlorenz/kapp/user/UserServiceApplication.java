package co.edu.konradlorenz.kapp.user;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Owns user profiles: names, picture, roles and activation state. It keeps no identity
 * document, no phone number and nothing academic: a student's program, pensum and level are
 * read from SINU when the student asks for their own profile.
 *
 * <p>It deliberately does NOT own credentials. Passwords, roles and e-mail verification
 * live in auth-service. Splitting them this way keeps authentication behind a single
 * seam, which is the piece that Microsoft Entra ID will replace once the university
 * grants an application registration.
 *
 * <p>Security is configured by {@code common}'s auto-configuration: no annotation or
 * component scan is required here.
 *
 * <p>{@code @EnableMongock} is NOT optional. Mongock 5.5.1 ships neither
 * {@code AutoConfiguration.imports} nor {@code spring.factories}, so nothing registers
 * it automatically: without this annotation the migrations are silently skipped, indexes
 * are never created and the failure only shows up as duplicate data much later. Every
 * KApp service that talks to MongoDB must carry it.
 */
@SpringBootApplication
// Activates CredentialStatusClient: deactivating an account has to suspend the credential in
// auth-service, because that is where sign-in is decided.
@EnableFeignClients
@EnableMongock
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

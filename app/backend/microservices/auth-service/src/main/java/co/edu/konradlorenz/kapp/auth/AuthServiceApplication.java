package co.edu.konradlorenz.kapp.auth;

import co.edu.konradlorenz.kapp.auth.config.RegistrationProperties;
import co.edu.konradlorenz.kapp.auth.jwt.JwtProperties;
import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Owns credentials: e-mail, password hash, roles, e-mail verification state, and the
 * RS256 key pair that signs every access token on the platform.
 *
 * <p>It deliberately does NOT own profiles. Names, identification, contact details and
 * academic placement live in user-service, which this service calls exactly once, during
 * registration. Splitting them this way keeps authentication behind a single seam, which
 * is the piece Microsoft Entra ID replaces once the university grants an application
 * registration - see {@code identity.EntraIdAdapter}.
 *
 * <p>{@code @EnableFeignClients} activates {@code UserProfileClient}, that single outbound
 * call.
 *
 * <p>Security comes from {@code common}'s auto-configuration, which contributes the
 * catch-all chain; {@code config.AuthSecurityConfig} adds the narrower chain that makes
 * login, registration, verification and the JWKS document public.
 *
 * <p>{@code @EnableMongock} is NOT optional. Mongock 5.5.1 ships neither
 * {@code AutoConfiguration.imports} nor {@code spring.factories}, so nothing registers
 * it automatically: without this annotation the migrations are silently skipped, indexes
 * are never created and the failure only shows up as duplicate data much later. Every
 * KApp service that talks to MongoDB must carry it.
 */
@SpringBootApplication
@EnableMongock
@EnableFeignClients
@EnableConfigurationProperties({JwtProperties.class, RegistrationProperties.class})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}

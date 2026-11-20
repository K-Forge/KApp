package co.edu.konradlorenz.kapp.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@link MicrosoftProperties}, which the sign-in with Microsoft reads. */
@Configuration
@EnableConfigurationProperties(MicrosoftProperties.class)
public class MicrosoftConfig {
}

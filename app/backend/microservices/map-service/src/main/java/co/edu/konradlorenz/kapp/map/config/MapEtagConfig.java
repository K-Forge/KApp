package co.edu.konradlorenz.kapp.map.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * Every successful read of the map carries an {@code ETag}, and a client that sends it back in
 * {@code If-None-Match} gets {@code 304 Not Modified} with no body while nothing changed. A phone
 * opens the same buildings and floors over and over, and a floor is the largest answer this
 * service gives.
 *
 * <p>The tag is a hash of the answer itself, so it cannot go stale: there is no version to forget
 * to bump when a building, a floor, a space or the ground changes. The answer is still built on
 * every request; what the client saves is the download.
 *
 * <p>Registered without an order, which puts it after Spring Security's filter chain: a request
 * that is not allowed to read the map never reaches it. Only {@code GET} and {@code HEAD} answers
 * in the 2xx range are tagged, so writes go through untouched.
 */
@Configuration
public class MapEtagConfig {

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> mapEtagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/api/map/*");
        registration.setName("mapEtagFilter");
        return registration;
    }
}

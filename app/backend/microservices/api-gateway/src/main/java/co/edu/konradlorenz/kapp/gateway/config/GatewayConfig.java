package co.edu.konradlorenz.kapp.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de rutas del API Gateway.
 * Define el enrutamiento hacia los diferentes microservicios.
 */
@Configuration
public class GatewayConfig {

        @Bean
        public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
                return builder.routes()
                                // Auth Service - Rutas públicas de autenticación
                                .route("auth-service", r -> r
                                                .path("/auth/**")
                                                .uri("lb://auth-service"))

                                // User Service - Gestión de usuarios
                                .route("user-service", r -> r
                                                .path("/api/users/**", "/api/admin/people/**", "/api/admin/members/**",
                                                                "/api/admin/students/**", "/api/admin/employees/**")
                                                .uri("lb://user-service"))

                                // Course Service - Gestión de cursos
                                .route("course-service", r -> r
                                                .path("/api/courses/**", "/api/admin/courses/**",
                                                                "/api/admin/groups/**",
                                                                "/api/admin/programs/**", "/api/student/courses/**",
                                                                "/api/professor/courses/**")
                                                .uri("lb://course-service"))

                                // Assignment Service - Gestión de tareas
                                .route("assignment-service", r -> r
                                                .path("/api/assignments/**", "/api/admin/assignments/**",
                                                                "/api/student/assignments/**",
                                                                "/api/professor/assignments/**",
                                                                "/api/professor/submissions/**")
                                                .uri("lb://assignment-service"))

                                .build();
        }
}

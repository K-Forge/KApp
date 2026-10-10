package co.edu.konradlorenz.kapp.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway - Punto de entrada único para todos los microservicios.
 * Maneja el enrutamiento, balanceo de carga, y validación de JWT.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

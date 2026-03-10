package co.edu.konradlorenz.kapp.assignment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Assignment Service - Microservicio de Gestión de Tareas.
 * Administra tareas, entregas y calificaciones.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class AssignmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssignmentServiceApplication.class, args);
    }
}

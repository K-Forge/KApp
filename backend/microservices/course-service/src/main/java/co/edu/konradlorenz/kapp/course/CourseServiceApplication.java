package co.edu.konradlorenz.kapp.course;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Course Service - Microservicio de Gestión de Cursos.
 * Administra cursos, grupos, programas y matrículas.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class CourseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourseServiceApplication.class, args);
    }
}

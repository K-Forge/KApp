package co.edu.konradlorenz.kapp.assignment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Cliente Feign para comunicación con el Course Service.
 */
@FeignClient(name = "course-service", path = "/api/courses/internal")
public interface CourseServiceClient {

    @GetMapping("/professor-access")
    Boolean checkProfessorAccess(@RequestParam("email") String email,
            @RequestParam("groupId") Long groupId);
}

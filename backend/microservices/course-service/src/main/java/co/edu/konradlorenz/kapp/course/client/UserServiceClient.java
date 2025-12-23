package co.edu.konradlorenz.kapp.course.client;

import co.edu.konradlorenz.kapp.common.dto.StudentSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Cliente Feign para comunicación con el User Service.
 */
@FeignClient(name = "user-service", path = "/api/users/internal")
public interface UserServiceClient {

    @GetMapping("/student/by-email")
    StudentSummaryDTO getStudentByEmail(@RequestParam("email") String email);

    @GetMapping("/student/id/by-email")
    Long getStudentIdByEmail(@RequestParam("email") String email);

    @GetMapping("/employee/id/by-email")
    Long getEmployeeIdByEmail(@RequestParam("email") String email);
}

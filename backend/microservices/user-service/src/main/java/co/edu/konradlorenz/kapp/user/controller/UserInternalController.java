package co.edu.konradlorenz.kapp.user.controller;

import co.edu.konradlorenz.kapp.common.dto.StudentSummaryDTO;
import co.edu.konradlorenz.kapp.user.entity.Student;
import co.edu.konradlorenz.kapp.user.entity.Employee;
import co.edu.konradlorenz.kapp.user.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador interno para comunicación entre microservicios.
 * Proporciona datos de usuarios a otros servicios.
 */
@RestController
@RequestMapping("/api/users/internal")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserAdminService userAdminService;

    /**
     * Obtiene información de estudiante por email.
     * Usado por otros microservicios.
     */
    @GetMapping("/student/by-email")
    public ResponseEntity<StudentSummaryDTO> getStudentByEmail(@RequestParam String email) {
        Student student = userAdminService.getStudentByEmail(email);

        StudentSummaryDTO dto = new StudentSummaryDTO();
        dto.setId(student.getId());
        dto.setStudentCode(student.getStudentCode());
        dto.setEmail(student.getMember().getUniversityEmail());
        dto.setFullName(student.getMember().getPerson().getFirstName() + " " +
                student.getMember().getPerson().getLastName());

        return ResponseEntity.ok(dto);
    }

    /**
     * Obtiene ID de estudiante por email.
     */
    @GetMapping("/student/id/by-email")
    public ResponseEntity<Long> getStudentIdByEmail(@RequestParam String email) {
        Student student = userAdminService.getStudentByEmail(email);
        return ResponseEntity.ok(student.getId());
    }

    /**
     * Obtiene ID de empleado (profesor) por email.
     */
    @GetMapping("/employee/id/by-email")
    public ResponseEntity<Long> getEmployeeIdByEmail(@RequestParam String email) {
        Employee employee = userAdminService.getEmployeeByEmail(email);
        return ResponseEntity.ok(employee.getId());
    }
}

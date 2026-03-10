package co.edu.konradlorenz.kapp.course.controller;

import co.edu.konradlorenz.kapp.course.service.ProfessorCourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador interno para comunicación entre microservicios.
 */
@RestController
@RequestMapping("/api/courses/internal")
@RequiredArgsConstructor
public class CourseInternalController {

    private final ProfessorCourseService professorCourseService;

    /**
     * Verifica si un profesor tiene acceso a un grupo.
     */
    @GetMapping("/professor-access")
    public ResponseEntity<Boolean> checkProfessorAccess(
            @RequestParam String email,
            @RequestParam Long groupId) {
        boolean hasAccess = professorCourseService.hasProfessorAccessToGroup(email, groupId);
        return ResponseEntity.ok(hasAccess);
    }
}

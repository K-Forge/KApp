package co.edu.konradlorenz.kapp.course.controller;

import co.edu.konradlorenz.kapp.common.dto.CourseDTO;
import co.edu.konradlorenz.kapp.common.dto.StudentSummaryDTO;
import co.edu.konradlorenz.kapp.course.service.ProfessorCourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de cursos para profesores.
 */
@RestController
@RequestMapping("/api/professor/courses")
@RequiredArgsConstructor
public class ProfessorCourseController {

    private final ProfessorCourseService professorCourseService;

    /**
     * Obtiene los cursos que imparte el profesor actual.
     */
    @GetMapping
    public ResponseEntity<List<CourseDTO>> getTeachingCourses(
            @RequestHeader("X-User-Email") String email) {
        return ResponseEntity.ok(professorCourseService.getTeachingCourses(email));
    }

    /**
     * Obtiene los estudiantes de un grupo específico.
     */
    @GetMapping("/{groupId}/students")
    public ResponseEntity<List<StudentSummaryDTO>> getGroupStudents(
            @RequestHeader("X-User-Email") String email,
            @PathVariable Long groupId) {
        return ResponseEntity.ok(professorCourseService.getGroupStudents(email, groupId));
    }
}

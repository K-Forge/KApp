package co.edu.konradlorenz.kapp.course.controller;

import co.edu.konradlorenz.kapp.common.dto.CourseDTO;
import co.edu.konradlorenz.kapp.course.service.StudentCourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de cursos para estudiantes.
 */
@RestController
@RequestMapping("/api/student/courses")
@RequiredArgsConstructor
public class StudentCourseController {

    private final StudentCourseService studentCourseService;

    /**
     * Obtiene los cursos inscritos del estudiante actual.
     */
    @GetMapping
    public ResponseEntity<List<CourseDTO>> getEnrolledCourses(
            @RequestHeader("X-User-Email") String email) {
        return ResponseEntity.ok(studentCourseService.getEnrolledCourses(email));
    }
}

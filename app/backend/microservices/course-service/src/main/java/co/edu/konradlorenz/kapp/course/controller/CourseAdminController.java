package co.edu.konradlorenz.kapp.course.controller;

import co.edu.konradlorenz.kapp.common.dto.*;
import co.edu.konradlorenz.kapp.course.entity.*;
import co.edu.konradlorenz.kapp.course.service.CourseAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de administración de cursos.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class CourseAdminController {

    private final CourseAdminService courseAdminService;

    // ==================== COURSES ====================

    @GetMapping("/courses")
    public ResponseEntity<List<Course>> getAllCourses() {
        return ResponseEntity.ok(courseAdminService.getAllCourses());
    }

    @GetMapping("/courses/{id}")
    public ResponseEntity<Course> getCourse(@PathVariable Long id) {
        return ResponseEntity.ok(courseAdminService.getCourse(id));
    }

    @PostMapping("/courses")
    public ResponseEntity<Course> createCourse(@RequestBody CourseCrudDTO dto) {
        return ResponseEntity.ok(courseAdminService.createCourse(dto));
    }

    @PutMapping("/courses/{id}")
    public ResponseEntity<Course> updateCourse(@PathVariable Long id, @RequestBody CourseCrudDTO dto) {
        return ResponseEntity.ok(courseAdminService.updateCourse(id, dto));
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        courseAdminService.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== COURSE GROUPS ====================

    @GetMapping("/groups")
    public ResponseEntity<List<CourseGroup>> getAllCourseGroups() {
        return ResponseEntity.ok(courseAdminService.getAllCourseGroups());
    }

    @GetMapping("/groups/{id}")
    public ResponseEntity<CourseGroup> getCourseGroup(@PathVariable Long id) {
        return ResponseEntity.ok(courseAdminService.getCourseGroup(id));
    }

    @PostMapping("/groups")
    public ResponseEntity<CourseGroup> createCourseGroup(@RequestBody CourseGroupDTO dto) {
        return ResponseEntity.ok(courseAdminService.createCourseGroup(dto));
    }

    @PutMapping("/groups/{id}")
    public ResponseEntity<CourseGroup> updateCourseGroup(@PathVariable Long id, @RequestBody CourseGroupDTO dto) {
        return ResponseEntity.ok(courseAdminService.updateCourseGroup(id, dto));
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteCourseGroup(@PathVariable Long id) {
        courseAdminService.deleteCourseGroup(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== PROGRAMS ====================

    @GetMapping("/programs")
    public ResponseEntity<List<Program>> getAllPrograms() {
        return ResponseEntity.ok(courseAdminService.getAllPrograms());
    }
}

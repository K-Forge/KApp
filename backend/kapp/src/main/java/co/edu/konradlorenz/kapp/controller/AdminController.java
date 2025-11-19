package co.edu.konradlorenz.kapp.controller;

import co.edu.konradlorenz.kapp.entity.Course;
import co.edu.konradlorenz.kapp.entity.Person;
import co.edu.konradlorenz.kapp.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/people")
    public ResponseEntity<List<Person>> getAllPeople() {
        return ResponseEntity.ok(adminService.getAllPeople());
    }

    @GetMapping("/courses")
    public ResponseEntity<List<Course>> getAllCourses() {
        return ResponseEntity.ok(adminService.getAllCourses());
    }
}

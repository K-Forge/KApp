package co.edu.konradlorenz.kapp.controller;

import co.edu.konradlorenz.kapp.dto.*;
import co.edu.konradlorenz.kapp.entity.*;
import co.edu.konradlorenz.kapp.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // --- People ---
    @GetMapping("/people") public ResponseEntity<List<Person>> getAllPeople() { return ResponseEntity.ok(adminService.getAllPeople()); }
    @GetMapping("/people/{id}") public ResponseEntity<Person> getPerson(@PathVariable Long id) { return ResponseEntity.ok(adminService.getPerson(id)); }
    @PostMapping("/people") public ResponseEntity<Person> createPerson(@RequestBody PersonDTO dto) { return ResponseEntity.ok(adminService.createPerson(dto)); }
    @PutMapping("/people/{id}") public ResponseEntity<Person> updatePerson(@PathVariable Long id, @RequestBody PersonDTO dto) { return ResponseEntity.ok(adminService.updatePerson(id, dto)); }
    @DeleteMapping("/people/{id}") public ResponseEntity<Void> deletePerson(@PathVariable Long id) { adminService.deletePerson(id); return ResponseEntity.noContent().build(); }

    // --- Members ---
    @GetMapping("/members") public ResponseEntity<List<Member>> getAllMembers() { return ResponseEntity.ok(adminService.getAllMembers()); }
    @GetMapping("/members/{id}") public ResponseEntity<Member> getMember(@PathVariable Long id) { return ResponseEntity.ok(adminService.getMember(id)); }
    @PostMapping("/members") public ResponseEntity<Member> createMember(@RequestBody MemberDTO dto) { return ResponseEntity.ok(adminService.createMember(dto)); }
    @PutMapping("/members/{id}") public ResponseEntity<Member> updateMember(@PathVariable Long id, @RequestBody MemberDTO dto) { return ResponseEntity.ok(adminService.updateMember(id, dto)); }
    @DeleteMapping("/members/{id}") public ResponseEntity<Void> deleteMember(@PathVariable Long id) { adminService.deleteMember(id); return ResponseEntity.noContent().build(); }

    // --- Students ---
    @GetMapping("/students") public ResponseEntity<List<Student>> getAllStudents() { return ResponseEntity.ok(adminService.getAllStudents()); }
    @GetMapping("/students/{id}") public ResponseEntity<Student> getStudent(@PathVariable Long id) { return ResponseEntity.ok(adminService.getStudent(id)); }
    @PostMapping("/students") public ResponseEntity<Student> createStudent(@RequestBody StudentDTO dto) { return ResponseEntity.ok(adminService.createStudent(dto)); }
    @PutMapping("/students/{id}") public ResponseEntity<Student> updateStudent(@PathVariable Long id, @RequestBody StudentDTO dto) { return ResponseEntity.ok(adminService.updateStudent(id, dto)); }
    @DeleteMapping("/students/{id}") public ResponseEntity<Void> deleteStudent(@PathVariable Long id) { adminService.deleteStudent(id); return ResponseEntity.noContent().build(); }

    // --- Employees ---
    @GetMapping("/employees") public ResponseEntity<List<Employee>> getAllEmployees() { return ResponseEntity.ok(adminService.getAllEmployees()); }
    @GetMapping("/employees/{id}") public ResponseEntity<Employee> getEmployee(@PathVariable Long id) { return ResponseEntity.ok(adminService.getEmployee(id)); }
    @PostMapping("/employees") public ResponseEntity<Employee> createEmployee(@RequestBody EmployeeDTO dto) { return ResponseEntity.ok(adminService.createEmployee(dto)); }
    @PutMapping("/employees/{id}") public ResponseEntity<Employee> updateEmployee(@PathVariable Long id, @RequestBody EmployeeDTO dto) { return ResponseEntity.ok(adminService.updateEmployee(id, dto)); }
    @DeleteMapping("/employees/{id}") public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) { adminService.deleteEmployee(id); return ResponseEntity.noContent().build(); }

    // --- Courses ---
    @GetMapping("/courses") public ResponseEntity<List<Course>> getAllCourses() { return ResponseEntity.ok(adminService.getAllCourses()); }
    @GetMapping("/courses/{id}") public ResponseEntity<Course> getCourse(@PathVariable Long id) { return ResponseEntity.ok(adminService.getCourse(id)); }
    @PostMapping("/courses") public ResponseEntity<Course> createCourse(@RequestBody CourseCrudDTO dto) { return ResponseEntity.ok(adminService.createCourse(dto)); }
    @PutMapping("/courses/{id}") public ResponseEntity<Course> updateCourse(@PathVariable Long id, @RequestBody CourseCrudDTO dto) { return ResponseEntity.ok(adminService.updateCourse(id, dto)); }
    @DeleteMapping("/courses/{id}") public ResponseEntity<Void> deleteCourse(@PathVariable Long id) { adminService.deleteCourse(id); return ResponseEntity.noContent().build(); }

    // --- Course Groups ---
    @GetMapping("/groups") public ResponseEntity<List<CourseGroup>> getAllCourseGroups() { return ResponseEntity.ok(adminService.getAllCourseGroups()); }
    @GetMapping("/groups/{id}") public ResponseEntity<CourseGroup> getCourseGroup(@PathVariable Long id) { return ResponseEntity.ok(adminService.getCourseGroup(id)); }
    @PostMapping("/groups") public ResponseEntity<CourseGroup> createCourseGroup(@RequestBody CourseGroupDTO dto) { return ResponseEntity.ok(adminService.createCourseGroup(dto)); }
    @PutMapping("/groups/{id}") public ResponseEntity<CourseGroup> updateCourseGroup(@PathVariable Long id, @RequestBody CourseGroupDTO dto) { return ResponseEntity.ok(adminService.updateCourseGroup(id, dto)); }
    @DeleteMapping("/groups/{id}") public ResponseEntity<Void> deleteCourseGroup(@PathVariable Long id) { adminService.deleteCourseGroup(id); return ResponseEntity.noContent().build(); }

    // --- Assignments ---
    @GetMapping("/assignments") public ResponseEntity<List<Assignment>> getAllAssignments() { return ResponseEntity.ok(adminService.getAllAssignments()); }
    @GetMapping("/assignments/{id}") public ResponseEntity<Assignment> getAssignment(@PathVariable Long id) { return ResponseEntity.ok(adminService.getAssignment(id)); }
    @PostMapping("/assignments") public ResponseEntity<Assignment> createAssignment(@RequestBody AssignmentDTO dto) { return ResponseEntity.ok(adminService.createAssignment(dto)); }
    @PutMapping("/assignments/{id}") public ResponseEntity<Assignment> updateAssignment(@PathVariable Long id, @RequestBody AssignmentDTO dto) { return ResponseEntity.ok(adminService.updateAssignment(id, dto)); }
    @DeleteMapping("/assignments/{id}") public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) { adminService.deleteAssignment(id); return ResponseEntity.noContent().build(); }
}

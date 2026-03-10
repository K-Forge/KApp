package co.edu.konradlorenz.kapp.user.controller;

import co.edu.konradlorenz.kapp.common.dto.*;
import co.edu.konradlorenz.kapp.user.entity.*;
import co.edu.konradlorenz.kapp.user.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de administración de usuarios.
 * Expone endpoints CRUD para personas, miembros, estudiantes y empleados.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    // ==================== PEOPLE ENDPOINTS ====================

    @GetMapping("/people")
    public ResponseEntity<List<Person>> getAllPeople() {
        return ResponseEntity.ok(userAdminService.getAllPeople());
    }

    @GetMapping("/people/{id}")
    public ResponseEntity<Person> getPerson(@PathVariable Long id) {
        return ResponseEntity.ok(userAdminService.getPerson(id));
    }

    @PostMapping("/people")
    public ResponseEntity<Person> createPerson(@RequestBody PersonDTO dto) {
        return ResponseEntity.ok(userAdminService.createPerson(dto));
    }

    @PutMapping("/people/{id}")
    public ResponseEntity<Person> updatePerson(@PathVariable Long id, @RequestBody PersonDTO dto) {
        return ResponseEntity.ok(userAdminService.updatePerson(id, dto));
    }

    @DeleteMapping("/people/{id}")
    public ResponseEntity<Void> deletePerson(@PathVariable Long id) {
        userAdminService.deletePerson(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== MEMBERS ENDPOINTS ====================

    @GetMapping("/members")
    public ResponseEntity<List<Member>> getAllMembers() {
        return ResponseEntity.ok(userAdminService.getAllMembers());
    }

    @GetMapping("/members/{id}")
    public ResponseEntity<Member> getMember(@PathVariable Long id) {
        return ResponseEntity.ok(userAdminService.getMember(id));
    }

    @PostMapping("/members")
    public ResponseEntity<Member> createMember(@RequestBody MemberDTO dto) {
        return ResponseEntity.ok(userAdminService.createMember(dto));
    }

    @PutMapping("/members/{id}")
    public ResponseEntity<Member> updateMember(@PathVariable Long id, @RequestBody MemberDTO dto) {
        return ResponseEntity.ok(userAdminService.updateMember(id, dto));
    }

    @DeleteMapping("/members/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable Long id) {
        userAdminService.deleteMember(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== STUDENTS ENDPOINTS ====================

    @GetMapping("/students")
    public ResponseEntity<List<Student>> getAllStudents() {
        return ResponseEntity.ok(userAdminService.getAllStudents());
    }

    @GetMapping("/students/{id}")
    public ResponseEntity<Student> getStudent(@PathVariable Long id) {
        return ResponseEntity.ok(userAdminService.getStudent(id));
    }

    @PostMapping("/students")
    public ResponseEntity<Student> createStudent(@RequestBody StudentDTO dto) {
        return ResponseEntity.ok(userAdminService.createStudent(dto));
    }

    @PutMapping("/students/{id}")
    public ResponseEntity<Student> updateStudent(@PathVariable Long id, @RequestBody StudentDTO dto) {
        return ResponseEntity.ok(userAdminService.updateStudent(id, dto));
    }

    @DeleteMapping("/students/{id}")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
        userAdminService.deleteStudent(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== EMPLOYEES ENDPOINTS ====================

    @GetMapping("/employees")
    public ResponseEntity<List<Employee>> getAllEmployees() {
        return ResponseEntity.ok(userAdminService.getAllEmployees());
    }

    @GetMapping("/employees/{id}")
    public ResponseEntity<Employee> getEmployee(@PathVariable Long id) {
        return ResponseEntity.ok(userAdminService.getEmployee(id));
    }

    @PostMapping("/employees")
    public ResponseEntity<Employee> createEmployee(@RequestBody EmployeeDTO dto) {
        return ResponseEntity.ok(userAdminService.createEmployee(dto));
    }

    @PutMapping("/employees/{id}")
    public ResponseEntity<Employee> updateEmployee(@PathVariable Long id, @RequestBody EmployeeDTO dto) {
        return ResponseEntity.ok(userAdminService.updateEmployee(id, dto));
    }

    @DeleteMapping("/employees/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        userAdminService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }
}

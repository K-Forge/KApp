package co.edu.konradlorenz.kapp.assignment.controller;

import co.edu.konradlorenz.kapp.assignment.entity.Assignment;
import co.edu.konradlorenz.kapp.assignment.service.AssignmentAdminService;
import co.edu.konradlorenz.kapp.common.dto.AssignmentDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de administración de tareas.
 */
@RestController
@RequestMapping("/api/admin/assignments")
@RequiredArgsConstructor
public class AssignmentAdminController {

    private final AssignmentAdminService assignmentAdminService;

    @GetMapping
    public ResponseEntity<List<Assignment>> getAllAssignments() {
        return ResponseEntity.ok(assignmentAdminService.getAllAssignments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Assignment> getAssignment(@PathVariable Long id) {
        return ResponseEntity.ok(assignmentAdminService.getAssignment(id));
    }

    @PostMapping
    public ResponseEntity<Assignment> createAssignment(@RequestBody AssignmentDTO dto) {
        return ResponseEntity.ok(assignmentAdminService.createAssignment(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Assignment> updateAssignment(
            @PathVariable Long id,
            @RequestBody AssignmentDTO dto) {
        return ResponseEntity.ok(assignmentAdminService.updateAssignment(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) {
        assignmentAdminService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }
}

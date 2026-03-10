package co.edu.konradlorenz.kapp.assignment.controller;

import co.edu.konradlorenz.kapp.assignment.service.StudentAssignmentService;
import co.edu.konradlorenz.kapp.common.dto.AssignmentDTO;
import co.edu.konradlorenz.kapp.common.dto.SubmissionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de tareas para estudiantes.
 */
@RestController
@RequestMapping("/api/student/assignments")
@RequiredArgsConstructor
public class StudentAssignmentController {

    private final StudentAssignmentService studentAssignmentService;

    /**
     * Obtiene las tareas pendientes del estudiante.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<AssignmentDTO>> getPendingAssignments(
            @RequestHeader("X-User-Email") String email,
            @RequestParam List<Long> groupIds) {
        return ResponseEntity.ok(studentAssignmentService.getPendingAssignments(email, groupIds));
    }

    /**
     * Obtiene las tareas ya entregadas.
     */
    @GetMapping("/submitted")
    public ResponseEntity<List<AssignmentDTO>> getSubmittedAssignments(
            @RequestHeader("X-User-Email") String email,
            @RequestParam List<Long> groupIds) {
        return ResponseEntity.ok(studentAssignmentService.getSubmittedAssignments(email, groupIds));
    }

    /**
     * Envía una entrega de tarea.
     */
    @PostMapping("/{assignmentId}/submit")
    public ResponseEntity<SubmissionDTO> submitAssignment(
            @RequestHeader("X-User-Email") String email,
            @PathVariable Long assignmentId,
            @RequestBody String contentUrl) {
        return ResponseEntity.ok(studentAssignmentService.submitAssignment(email, assignmentId, contentUrl));
    }
}

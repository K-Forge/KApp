package co.edu.konradlorenz.kapp.assignment.controller;

import co.edu.konradlorenz.kapp.assignment.service.ProfessorAssignmentService;
import co.edu.konradlorenz.kapp.common.dto.AssignmentDTO;
import co.edu.konradlorenz.kapp.common.dto.SubmissionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de tareas para profesores.
 */
@RestController
@RequestMapping("/api/professor")
@RequiredArgsConstructor
public class ProfessorAssignmentController {

    private final ProfessorAssignmentService professorAssignmentService;

    /**
     * Crea una nueva tarea.
     */
    @PostMapping("/assignments")
    public ResponseEntity<AssignmentDTO> createAssignment(
            @RequestHeader("X-User-Email") String email,
            @RequestBody AssignmentDTO dto) {
        return ResponseEntity.ok(professorAssignmentService.createAssignment(email, dto));
    }

    /**
     * Obtiene las tareas del profesor.
     */
    @GetMapping("/assignments")
    public ResponseEntity<List<AssignmentDTO>> getProfessorAssignments(
            @RequestParam List<Long> groupIds) {
        return ResponseEntity.ok(professorAssignmentService.getProfessorAssignments(groupIds));
    }

    /**
     * Obtiene una tarea por ID.
     */
    @GetMapping("/assignments/{id}")
    public ResponseEntity<AssignmentDTO> getAssignment(
            @RequestHeader("X-User-Email") String email,
            @PathVariable Long id) {
        return ResponseEntity.ok(professorAssignmentService.getAssignment(email, id));
    }

    /**
     * Actualiza una tarea.
     */
    @PutMapping("/assignments/{id}")
    public ResponseEntity<AssignmentDTO> updateAssignment(
            @RequestHeader("X-User-Email") String email,
            @PathVariable Long id,
            @RequestBody AssignmentDTO dto) {
        return ResponseEntity.ok(professorAssignmentService.updateAssignment(email, id, dto));
    }

    /**
     * Elimina una tarea.
     */
    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<Void> deleteAssignment(
            @RequestHeader("X-User-Email") String email,
            @PathVariable Long id) {
        professorAssignmentService.deleteAssignment(email, id);
        return ResponseEntity.ok().build();
    }

    /**
     * Califica una entrega.
     */
    @PostMapping("/submissions/{submissionId}/grade")
    public ResponseEntity<SubmissionDTO> gradeSubmission(
            @PathVariable Long submissionId,
            @RequestParam Double grade,
            @RequestParam String feedback) {
        return ResponseEntity.ok(professorAssignmentService.gradeSubmission(submissionId, grade, feedback));
    }
}

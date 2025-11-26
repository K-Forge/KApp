package co.edu.konradlorenz.kapp.controller;

import co.edu.konradlorenz.kapp.dto.AssignmentDTO;
import co.edu.konradlorenz.kapp.dto.CourseDTO;
import co.edu.konradlorenz.kapp.dto.StudentSummaryDTO;
import co.edu.konradlorenz.kapp.dto.SubmissionDTO;
import co.edu.konradlorenz.kapp.service.ProfessorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/professor")
@PreAuthorize("hasRole('PROFESSOR')")
@RequiredArgsConstructor
public class ProfessorController {

    private final ProfessorService professorService;

    @GetMapping("/courses")
    public ResponseEntity<List<CourseDTO>> getTeachingCourses(Authentication authentication) {
        return ResponseEntity.ok(professorService.getTeachingCourses(authentication.getName()));
    }

    @PostMapping("/assignments")
    public ResponseEntity<AssignmentDTO> createAssignment(
            Authentication authentication,
            @RequestBody AssignmentDTO assignmentDTO) {
        return ResponseEntity.ok(professorService.createAssignment(authentication.getName(), assignmentDTO));
    }

    @PostMapping("/submissions/{submissionId}/grade")
    public ResponseEntity<SubmissionDTO> gradeSubmission(
            @PathVariable Long submissionId,
            @RequestParam Double grade,
            @RequestParam String feedback) {
        return ResponseEntity.ok(professorService.gradeSubmission(submissionId, grade, feedback));
    }

    @GetMapping("/courses/{groupId}/students")
    public ResponseEntity<List<StudentSummaryDTO>> getGroupStudents(
            Authentication authentication,
            @PathVariable Long groupId) {
        return ResponseEntity.ok(professorService.getGroupStudents(authentication.getName(), groupId));
    }

    @GetMapping("/assignments")
    public ResponseEntity<List<AssignmentDTO>> getProfessorAssignments(Authentication authentication) {
        return ResponseEntity.ok(professorService.getProfessorAssignments(authentication.getName()));
    }

    @GetMapping("/assignments/{id}")
    public ResponseEntity<AssignmentDTO> getAssignment(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(professorService.getAssignment(authentication.getName(), id));
    }

    @PutMapping("/assignments/{id}")
    public ResponseEntity<AssignmentDTO> updateAssignment(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody AssignmentDTO dto) {
        return ResponseEntity.ok(professorService.updateAssignment(authentication.getName(), id, dto));
    }

    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<Void> deleteAssignment(Authentication authentication, @PathVariable Long id) {
        professorService.deleteAssignment(authentication.getName(), id);
        return ResponseEntity.ok().build();
    }
}

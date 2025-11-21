package co.edu.konradlorenz.kapp.controller;

import co.edu.konradlorenz.kapp.dto.AssignmentDTO;
import co.edu.konradlorenz.kapp.dto.CourseDTO;
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
}

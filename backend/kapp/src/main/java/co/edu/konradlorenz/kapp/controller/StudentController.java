package co.edu.konradlorenz.kapp.controller;

import co.edu.konradlorenz.kapp.dto.AssignmentDTO;
import co.edu.konradlorenz.kapp.dto.CourseDTO;
import co.edu.konradlorenz.kapp.dto.SubmissionDTO;
import co.edu.konradlorenz.kapp.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @GetMapping("/courses")
    public ResponseEntity<List<CourseDTO>> getEnrolledCourses(Authentication authentication) {
        return ResponseEntity.ok(studentService.getEnrolledCourses(authentication.getName()));
    }

    @GetMapping("/assignments/pending")
    public ResponseEntity<List<AssignmentDTO>> getPendingAssignments(Authentication authentication) {
        return ResponseEntity.ok(studentService.getPendingAssignments(authentication.getName()));
    }

    @PostMapping("/assignments/{assignmentId}/submit")
    public ResponseEntity<SubmissionDTO> submitAssignment(
            Authentication authentication,
            @PathVariable Long assignmentId,
            @RequestBody String contentUrl) {
        return ResponseEntity.ok(studentService.submitAssignment(authentication.getName(), assignmentId, contentUrl));
    }

    @GetMapping("/assignments/submitted")
    public ResponseEntity<List<AssignmentDTO>> getSubmittedAssignments(Authentication authentication) {
        return ResponseEntity.ok(studentService.getSubmittedAssignments(authentication.getName()));
    }
}

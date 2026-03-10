package co.edu.konradlorenz.kapp.assignment.service;

import co.edu.konradlorenz.kapp.assignment.client.UserServiceClient;
import co.edu.konradlorenz.kapp.assignment.entity.Assignment;
import co.edu.konradlorenz.kapp.assignment.entity.Submission;
import co.edu.konradlorenz.kapp.assignment.repository.AssignmentRepository;
import co.edu.konradlorenz.kapp.assignment.repository.SubmissionRepository;
import co.edu.konradlorenz.kapp.common.dto.AssignmentDTO;
import co.edu.konradlorenz.kapp.common.dto.SubmissionDTO;
import co.edu.konradlorenz.kapp.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para operaciones de estudiantes con tareas.
 */
@Service
@RequiredArgsConstructor
public class StudentAssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final UserServiceClient userServiceClient;

    /**
     * Obtiene las tareas pendientes del estudiante.
     */
    @Transactional(readOnly = true)
    public List<AssignmentDTO> getPendingAssignments(String email, List<Long> groupIds) {
        Long studentId = userServiceClient.getStudentIdByEmail(email);

        List<AssignmentDTO> pending = new ArrayList<>();

        for (Long groupId : groupIds) {
            List<Assignment> assignments = assignmentRepository.findByCourseGroupId(groupId);
            for (Assignment a : assignments) {
                boolean submitted = submissionRepository
                        .findByAssignmentIdAndStudentId(a.getId(), studentId).isPresent();
                if (!submitted) {
                    pending.add(toDTO(a));
                }
            }
        }
        return pending;
    }

    /**
     * Obtiene las tareas ya entregadas del estudiante.
     */
    @Transactional(readOnly = true)
    public List<AssignmentDTO> getSubmittedAssignments(String email, List<Long> groupIds) {
        Long studentId = userServiceClient.getStudentIdByEmail(email);

        List<AssignmentDTO> submitted = new ArrayList<>();

        for (Long groupId : groupIds) {
            List<Assignment> assignments = assignmentRepository.findByCourseGroupId(groupId);
            for (Assignment a : assignments) {
                boolean isSubmitted = submissionRepository
                        .findByAssignmentIdAndStudentId(a.getId(), studentId).isPresent();
                if (isSubmitted) {
                    submitted.add(toDTO(a));
                }
            }
        }
        return submitted;
    }

    /**
     * Envía una entrega de tarea.
     */
    @Transactional
    public SubmissionDTO submitAssignment(String email, Long assignmentId, String contentUrl) {
        Long studentId = userServiceClient.getStudentIdByEmail(email);

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", assignmentId));

        if (submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId).isPresent()) {
            throw new IllegalArgumentException("Ya se ha entregado esta tarea");
        }

        Submission submission = new Submission();
        submission.setAssignment(assignment);
        submission.setStudentId(studentId);
        submission.setSubmissionDate(new Date());
        submission.setContentUrl(contentUrl);

        submission = submissionRepository.save(submission);

        return toSubmissionDTO(submission);
    }

    private AssignmentDTO toDTO(Assignment a) {
        AssignmentDTO dto = new AssignmentDTO();
        dto.setId(a.getId());
        dto.setCourseGroupId(a.getCourseGroupId());
        dto.setTitle(a.getTitle());
        dto.setDescription(a.getDescription());
        dto.setDueDate(a.getDueDate());
        dto.setMaxScore(a.getMaxScore());
        return dto;
    }

    private SubmissionDTO toSubmissionDTO(Submission s) {
        SubmissionDTO dto = new SubmissionDTO();
        dto.setId(s.getId());
        dto.setAssignmentId(s.getAssignment().getId());
        dto.setStudentId(s.getStudentId());
        dto.setSubmissionDate(s.getSubmissionDate());
        dto.setContentUrl(s.getContentUrl());
        dto.setGrade(s.getGrade());
        dto.setFeedback(s.getFeedback());
        return dto;
    }
}

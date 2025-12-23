package co.edu.konradlorenz.kapp.assignment.service;

import co.edu.konradlorenz.kapp.assignment.client.CourseServiceClient;
import co.edu.konradlorenz.kapp.assignment.entity.Assignment;
import co.edu.konradlorenz.kapp.assignment.entity.Submission;
import co.edu.konradlorenz.kapp.assignment.repository.AssignmentRepository;
import co.edu.konradlorenz.kapp.assignment.repository.SubmissionRepository;
import co.edu.konradlorenz.kapp.common.dto.AssignmentDTO;
import co.edu.konradlorenz.kapp.common.dto.SubmissionDTO;
import co.edu.konradlorenz.kapp.common.exception.AccessDeniedException;
import co.edu.konradlorenz.kapp.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para operaciones de profesores con tareas.
 */
@Service
@RequiredArgsConstructor
public class ProfessorAssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final CourseServiceClient courseServiceClient;

    /**
     * Crea una nueva tarea.
     */
    @Transactional
    public AssignmentDTO createAssignment(String email, AssignmentDTO dto) {
        // Verificar que el profesor tiene acceso al grupo
        Boolean hasAccess = courseServiceClient.checkProfessorAccess(email, dto.getCourseGroupId());
        if (!Boolean.TRUE.equals(hasAccess)) {
            throw new AccessDeniedException("No eres el profesor de este curso");
        }

        Assignment assignment = new Assignment();
        assignment.setCourseGroupId(dto.getCourseGroupId());
        assignment.setTitle(dto.getTitle());
        assignment.setDescription(dto.getDescription());
        assignment.setDueDate(dto.getDueDate());
        assignment.setMaxScore(dto.getMaxScore());

        assignment = assignmentRepository.save(assignment);
        dto.setId(assignment.getId());
        return dto;
    }

    /**
     * Obtiene las tareas del profesor.
     */
    @Transactional(readOnly = true)
    public List<AssignmentDTO> getProfessorAssignments(List<Long> groupIds) {
        List<Assignment> assignments = assignmentRepository.findByCourseGroupIdIn(groupIds);

        return assignments.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Obtiene una tarea por ID.
     */
    @Transactional(readOnly = true)
    public AssignmentDTO getAssignment(String email, Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", assignmentId));

        Boolean hasAccess = courseServiceClient.checkProfessorAccess(email, assignment.getCourseGroupId());
        if (!Boolean.TRUE.equals(hasAccess)) {
            throw new AccessDeniedException("No tienes acceso a esta tarea");
        }

        return toDTO(assignment);
    }

    /**
     * Actualiza una tarea.
     */
    @Transactional
    public AssignmentDTO updateAssignment(String email, Long assignmentId, AssignmentDTO dto) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", assignmentId));

        Boolean hasAccess = courseServiceClient.checkProfessorAccess(email, assignment.getCourseGroupId());
        if (!Boolean.TRUE.equals(hasAccess)) {
            throw new AccessDeniedException("No tienes acceso a esta tarea");
        }

        assignment.setTitle(dto.getTitle());
        assignment.setDescription(dto.getDescription());
        assignment.setDueDate(dto.getDueDate());
        assignment.setMaxScore(dto.getMaxScore());

        assignmentRepository.save(assignment);
        return dto;
    }

    /**
     * Elimina una tarea.
     */
    @Transactional
    public void deleteAssignment(String email, Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", assignmentId));

        Boolean hasAccess = courseServiceClient.checkProfessorAccess(email, assignment.getCourseGroupId());
        if (!Boolean.TRUE.equals(hasAccess)) {
            throw new AccessDeniedException("No tienes acceso a esta tarea");
        }

        assignmentRepository.delete(assignment);
    }

    /**
     * Califica una entrega.
     */
    @Transactional
    public SubmissionDTO gradeSubmission(Long submissionId, Double grade, String feedback) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", "id", submissionId));

        submission.setGrade(grade);
        submission.setFeedback(feedback);
        submissionRepository.save(submission);

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

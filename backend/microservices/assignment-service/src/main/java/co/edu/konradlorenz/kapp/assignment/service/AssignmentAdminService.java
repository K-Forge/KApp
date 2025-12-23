package co.edu.konradlorenz.kapp.assignment.service;

import co.edu.konradlorenz.kapp.assignment.entity.Assignment;
import co.edu.konradlorenz.kapp.assignment.repository.AssignmentRepository;
import co.edu.konradlorenz.kapp.common.dto.AssignmentDTO;
import co.edu.konradlorenz.kapp.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio de administración de tareas.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AssignmentAdminService {

    private final AssignmentRepository assignmentRepository;

    @Transactional(readOnly = true)
    public List<Assignment> getAllAssignments() {
        return assignmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Assignment getAssignment(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", "id", id));
    }

    public Assignment createAssignment(AssignmentDTO dto) {
        Assignment assignment = new Assignment();
        updateFromDTO(assignment, dto);
        return assignmentRepository.save(assignment);
    }

    public Assignment updateAssignment(Long id, AssignmentDTO dto) {
        Assignment assignment = getAssignment(id);
        updateFromDTO(assignment, dto);
        return assignmentRepository.save(assignment);
    }

    public void deleteAssignment(Long id) {
        assignmentRepository.deleteById(id);
    }

    private void updateFromDTO(Assignment assignment, AssignmentDTO dto) {
        if (dto.getCourseGroupId() != null) {
            assignment.setCourseGroupId(dto.getCourseGroupId());
        }
        assignment.setTitle(dto.getTitle());
        assignment.setDescription(dto.getDescription());
        assignment.setDueDate(dto.getDueDate());
        assignment.setMaxScore(dto.getMaxScore());
    }
}

package co.edu.konradlorenz.kapp.course.service;

import co.edu.konradlorenz.kapp.common.dto.*;
import co.edu.konradlorenz.kapp.common.exception.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.common.exception.AccessDeniedException;
import co.edu.konradlorenz.kapp.course.client.UserServiceClient;
import co.edu.konradlorenz.kapp.course.entity.*;
import co.edu.konradlorenz.kapp.course.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para operaciones de profesores con cursos.
 */
@Service
@RequiredArgsConstructor
public class ProfessorCourseService {

    private final CourseGroupRepository courseGroupRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final UserServiceClient userServiceClient;

    /**
     * Obtiene los cursos que imparte el profesor.
     */
    @Transactional(readOnly = true)
    public List<CourseDTO> getTeachingCourses(String email) {
        Long professorId = userServiceClient.getEmployeeIdByEmail(email);
        List<CourseGroup> groups = courseGroupRepository.findByProfessorId(professorId);

        return groups.stream().map(group -> {
            CourseDTO dto = new CourseDTO();
            dto.setCourseGroupId(group.getId());
            dto.setCourseName(group.getCourse().getName());
            dto.setCourseCode(group.getCourse().getCode());
            dto.setGroupCode(group.getGroupCode());
            dto.setProfessorName("Profesor");
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Obtiene los estudiantes de un grupo.
     */
    @Transactional(readOnly = true)
    public List<StudentSummaryDTO> getGroupStudents(String email, Long groupId) {
        Long professorId = userServiceClient.getEmployeeIdByEmail(email);

        CourseGroup group = courseGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("CourseGroup", "id", groupId));

        if (!group.getProfessorId().equals(professorId)) {
            throw new AccessDeniedException("No eres el profesor de este curso");
        }

        List<StudentCourse> enrollments = studentCourseRepository.findByGroupId(groupId);

        return enrollments.stream().map(enrollment -> {
            StudentSummaryDTO dto = new StudentSummaryDTO();
            dto.setId(enrollment.getStudentId());
            // TODO: Obtener información completa del user-service
            dto.setFullName("Estudiante " + enrollment.getStudentId());
            dto.setStudentCode("STU-" + enrollment.getStudentId());
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Verifica si el profesor tiene acceso a un grupo.
     */
    public boolean hasProfessorAccessToGroup(String email, Long groupId) {
        Long professorId = userServiceClient.getEmployeeIdByEmail(email);
        CourseGroup group = courseGroupRepository.findById(groupId).orElse(null);
        return group != null && group.getProfessorId().equals(professorId);
    }
}

package co.edu.konradlorenz.kapp.course.service;

import co.edu.konradlorenz.kapp.common.dto.*;
import co.edu.konradlorenz.kapp.common.exception.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.course.client.UserServiceClient;
import co.edu.konradlorenz.kapp.course.entity.*;
import co.edu.konradlorenz.kapp.course.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para operaciones de estudiantes con cursos.
 */
@Service
@RequiredArgsConstructor
public class StudentCourseService {

    private final StudentCourseRepository studentCourseRepository;
    private final UserServiceClient userServiceClient;

    /**
     * Obtiene los cursos inscritos del estudiante.
     */
    @Transactional(readOnly = true)
    public List<CourseDTO> getEnrolledCourses(String email) {
        Long studentId = userServiceClient.getStudentIdByEmail(email);
        List<StudentCourse> enrollments = studentCourseRepository.findByStudentId(studentId);

        return enrollments.stream().map(sc -> {
            CourseGroup group = sc.getGroup();
            CourseDTO dto = new CourseDTO();
            dto.setCourseGroupId(group.getId());
            dto.setCourseName(group.getCourse().getName());
            dto.setCourseCode(group.getCourse().getCode());
            dto.setGroupCode(group.getGroupCode());
            dto.setProfessorName("Profesor"); // TODO: Obtener del user-service
            return dto;
        }).collect(Collectors.toList());
    }
}

package co.edu.konradlorenz.kapp.course.service;

import co.edu.konradlorenz.kapp.common.dto.*;
import co.edu.konradlorenz.kapp.common.exception.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.course.entity.*;
import co.edu.konradlorenz.kapp.course.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio de administración de cursos.
 * Gestiona CRUD de cursos, grupos y programas.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CourseAdminService {

    private final CourseRepository courseRepository;
    private final CourseGroupRepository courseGroupRepository;
    private final ProgramRepository programRepository;
    private final AcademicPeriodRepository academicPeriodRepository;

    // ==================== COURSE CRUD ====================

    @Transactional(readOnly = true)
    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Course getCourse(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
    }

    public Course createCourse(CourseCrudDTO dto) {
        Course course = new Course();
        updateCourseFromDTO(course, dto);
        return courseRepository.save(course);
    }

    public Course updateCourse(Long id, CourseCrudDTO dto) {
        Course course = getCourse(id);
        updateCourseFromDTO(course, dto);
        return courseRepository.save(course);
    }

    public void deleteCourse(Long id) {
        courseRepository.deleteById(id);
    }

    private void updateCourseFromDTO(Course course, CourseCrudDTO dto) {
        if (dto.getProgramId() != null) {
            Program program = programRepository.findById(dto.getProgramId())
                    .orElseThrow(() -> new ResourceNotFoundException("Program", "id", dto.getProgramId()));
            course.setProgram(program);
        }
        course.setCode(dto.getCode());
        course.setName(dto.getName());
        course.setCredits(dto.getCredits());
        course.setLevel(dto.getLevel());
    }

    // ==================== COURSE GROUP CRUD ====================

    @Transactional(readOnly = true)
    public List<CourseGroup> getAllCourseGroups() {
        return courseGroupRepository.findAll();
    }

    @Transactional(readOnly = true)
    public CourseGroup getCourseGroup(Long id) {
        return courseGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseGroup", "id", id));
    }

    public CourseGroup createCourseGroup(CourseGroupDTO dto) {
        CourseGroup group = new CourseGroup();
        updateCourseGroupFromDTO(group, dto);
        return courseGroupRepository.save(group);
    }

    public CourseGroup updateCourseGroup(Long id, CourseGroupDTO dto) {
        CourseGroup group = getCourseGroup(id);
        updateCourseGroupFromDTO(group, dto);
        return courseGroupRepository.save(group);
    }

    public void deleteCourseGroup(Long id) {
        courseGroupRepository.deleteById(id);
    }

    private void updateCourseGroupFromDTO(CourseGroup group, CourseGroupDTO dto) {
        if (dto.getCourseId() != null) {
            group.setCourse(getCourse(dto.getCourseId()));
        }
        if (dto.getPeriodId() != null) {
            AcademicPeriod period = academicPeriodRepository.findById(dto.getPeriodId())
                    .orElseThrow(() -> new ResourceNotFoundException("AcademicPeriod", "id", dto.getPeriodId()));
            group.setPeriod(period);
        }
        if (dto.getProfessorId() != null) {
            group.setProfessorId(dto.getProfessorId());
        }
        group.setGroupCode(dto.getGroupCode());
    }

    // ==================== PROGRAMS ====================

    @Transactional(readOnly = true)
    public List<Program> getAllPrograms() {
        return programRepository.findAll();
    }
}

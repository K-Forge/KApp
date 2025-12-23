package co.edu.konradlorenz.kapp.course.repository;

import co.edu.konradlorenz.kapp.course.entity.StudentCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio para operaciones con StudentCourse.
 */
@Repository
public interface StudentCourseRepository extends JpaRepository<StudentCourse, Long> {
    List<StudentCourse> findByStudentId(Long studentId);

    List<StudentCourse> findByGroupId(Long groupId);
}

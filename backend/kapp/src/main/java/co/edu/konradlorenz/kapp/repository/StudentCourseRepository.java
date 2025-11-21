package co.edu.konradlorenz.kapp.repository;

import co.edu.konradlorenz.kapp.entity.StudentCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StudentCourseRepository extends JpaRepository<StudentCourse, Long> {
    List<StudentCourse> findByStudentId(Long studentId);
    List<StudentCourse> findByGroupId(Long groupId);
}

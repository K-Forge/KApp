package co.edu.konradlorenz.kapp.repository;

import co.edu.konradlorenz.kapp.entity.StudentCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentCourseRepository extends JpaRepository<StudentCourse, Long> {
    List<StudentCourse> findByStudentId(Long studentId);
    
    @Query("SELECT sc FROM StudentCourse sc WHERE sc.group.id = :groupId")
    List<StudentCourse> findByGroupId(@Param("groupId") Long groupId);
}

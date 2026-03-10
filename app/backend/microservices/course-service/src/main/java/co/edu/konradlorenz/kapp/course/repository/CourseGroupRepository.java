package co.edu.konradlorenz.kapp.course.repository;

import co.edu.konradlorenz.kapp.course.entity.CourseGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio para operaciones con CourseGroup.
 */
@Repository
public interface CourseGroupRepository extends JpaRepository<CourseGroup, Long> {
    List<CourseGroup> findByProfessorId(Long professorId);
}

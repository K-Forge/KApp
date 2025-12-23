package co.edu.konradlorenz.kapp.course.repository;

import co.edu.konradlorenz.kapp.course.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para operaciones con Course.
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
}

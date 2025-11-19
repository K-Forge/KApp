package co.edu.konradlorenz.kapp.repository;

import co.edu.konradlorenz.kapp.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {
}

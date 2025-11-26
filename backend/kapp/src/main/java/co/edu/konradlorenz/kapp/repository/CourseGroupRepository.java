package co.edu.konradlorenz.kapp.repository;

import co.edu.konradlorenz.kapp.entity.CourseGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseGroupRepository extends JpaRepository<CourseGroup, Long> {
    List<CourseGroup> findByProfessorId(Long professorId);
}

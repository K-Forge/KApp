package co.edu.konradlorenz.kapp.repository;

import co.edu.konradlorenz.kapp.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByCourseGroupId(Long courseGroupId);
}

package co.edu.konradlorenz.kapp.assignment.repository;

import co.edu.konradlorenz.kapp.assignment.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio para operaciones con Assignment.
 */
@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    List<Assignment> findByCourseGroupId(Long courseGroupId);

    List<Assignment> findByCourseGroupIdIn(List<Long> courseGroupIds);
}

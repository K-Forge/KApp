package co.edu.konradlorenz.kapp.assignment.repository;

import co.edu.konradlorenz.kapp.assignment.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para operaciones con Submission.
 */
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByAssignmentId(Long assignmentId);

    Optional<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    List<Submission> findByStudentId(Long studentId);
}

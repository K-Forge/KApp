package co.edu.konradlorenz.kapp.course.repository;

import co.edu.konradlorenz.kapp.course.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para operaciones con Program.
 */
@Repository
public interface ProgramRepository extends JpaRepository<Program, Long> {
}

package co.edu.konradlorenz.kapp.repository;

import co.edu.konradlorenz.kapp.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramRepository extends JpaRepository<Program, Long> {
}

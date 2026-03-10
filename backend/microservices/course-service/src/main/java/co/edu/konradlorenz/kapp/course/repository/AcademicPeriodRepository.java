package co.edu.konradlorenz.kapp.course.repository;

import co.edu.konradlorenz.kapp.course.entity.AcademicPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para operaciones con AcademicPeriod.
 */
@Repository
public interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, Long> {
}

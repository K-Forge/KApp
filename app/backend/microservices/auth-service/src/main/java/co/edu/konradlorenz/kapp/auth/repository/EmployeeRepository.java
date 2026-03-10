package co.edu.konradlorenz.kapp.auth.repository;

import co.edu.konradlorenz.kapp.auth.entity.Employee;
import co.edu.konradlorenz.kapp.auth.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para operaciones con Employee.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByMember(Member member);
}

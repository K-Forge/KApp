package co.edu.konradlorenz.kapp.repository;

import co.edu.konradlorenz.kapp.entity.Employee;
import co.edu.konradlorenz.kapp.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByMember(Member member);
}

package co.edu.konradlorenz.kapp.auth.repository;

import co.edu.konradlorenz.kapp.auth.entity.Student;
import co.edu.konradlorenz.kapp.auth.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para operaciones con Student.
 */
@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByMember(Member member);
}

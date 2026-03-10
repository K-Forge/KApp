package co.edu.konradlorenz.kapp.repository;

import co.edu.konradlorenz.kapp.entity.Student;
import co.edu.konradlorenz.kapp.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByMember(Member member);
}

package co.edu.konradlorenz.kapp.repository;

import co.edu.konradlorenz.kapp.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByUniversityEmail(String universityEmail);
}

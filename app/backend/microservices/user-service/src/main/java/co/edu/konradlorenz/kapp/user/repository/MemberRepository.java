package co.edu.konradlorenz.kapp.user.repository;

import co.edu.konradlorenz.kapp.user.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para operaciones con Member.
 */
@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByUniversityEmail(String universityEmail);
}

package co.edu.konradlorenz.kapp.user.repository;

import co.edu.konradlorenz.kapp.user.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para operaciones con Person.
 */
@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {
}

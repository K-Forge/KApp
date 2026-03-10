package co.edu.konradlorenz.kapp.repository;

import co.edu.konradlorenz.kapp.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {
}

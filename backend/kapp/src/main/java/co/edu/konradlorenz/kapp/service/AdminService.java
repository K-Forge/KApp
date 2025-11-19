package co.edu.konradlorenz.kapp.service;

import co.edu.konradlorenz.kapp.entity.Course;
import co.edu.konradlorenz.kapp.entity.Person;
import co.edu.konradlorenz.kapp.repository.CourseRepository;
import co.edu.konradlorenz.kapp.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final PersonRepository personRepository;
    private final CourseRepository courseRepository;

    public List<Person> getAllPeople() {
        return personRepository.findAll();
    }

    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    // Add CRUD methods as needed
}

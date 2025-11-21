package co.edu.konradlorenz.kapp.service;

import co.edu.konradlorenz.kapp.dto.*;
import co.edu.konradlorenz.kapp.entity.*;
import co.edu.konradlorenz.kapp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final PersonRepository personRepository;
    private final MemberRepository memberRepository;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;
    private final CourseRepository courseRepository;
    private final CourseGroupRepository courseGroupRepository;
    private final AssignmentRepository assignmentRepository;
    private final ProgramRepository programRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final PasswordEncoder passwordEncoder;

    // --- Person CRUD ---
    public List<Person> getAllPeople() { return personRepository.findAll(); }
    public Person getPerson(Long id) { return personRepository.findById(id).orElseThrow(() -> new RuntimeException("Person not found")); }
    
    public Person createPerson(PersonDTO dto) {
        Person person = new Person();
        updatePersonFromDTO(person, dto);
        return personRepository.save(person);
    }

    public Person updatePerson(Long id, PersonDTO dto) {
        Person person = getPerson(id);
        updatePersonFromDTO(person, dto);
        return personRepository.save(person);
    }

    public void deletePerson(Long id) { personRepository.deleteById(id); }

    private void updatePersonFromDTO(Person person, PersonDTO dto) {
        person.setIdentificationNumber(dto.getIdentificationNumber());
        person.setIdentificationType(dto.getIdentificationType());
        person.setFirstName(dto.getFirstName());
        person.setLastName(dto.getLastName());
        person.setPhone(dto.getPhone());
        person.setHomeAddress(dto.getHomeAddress());
        person.setBirthDate(dto.getBirthDate());
    }

    // --- Member CRUD ---
    public List<Member> getAllMembers() { return memberRepository.findAll(); }
    public Member getMember(Long id) { return memberRepository.findById(id).orElseThrow(() -> new RuntimeException("Member not found")); }

    public Member createMember(MemberDTO dto) {
        Member member = new Member();
        Person person = getPerson(dto.getPersonId());
        member.setPerson(person);
        member.setUniversityCode(dto.getUniversityCode());
        member.setUniversityEmail(dto.getUniversityEmail());
        member.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        return memberRepository.save(member);
    }

    public Member updateMember(Long id, MemberDTO dto) {
        Member member = getMember(id);
        if (dto.getPersonId() != null) member.setPerson(getPerson(dto.getPersonId()));
        if (dto.getUniversityCode() != null) member.setUniversityCode(dto.getUniversityCode());
        if (dto.getUniversityEmail() != null) member.setUniversityEmail(dto.getUniversityEmail());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            member.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }
        return memberRepository.save(member);
    }

    public void deleteMember(Long id) { memberRepository.deleteById(id); }

    // --- Student CRUD ---
    public List<Student> getAllStudents() { return studentRepository.findAll(); }
    public Student getStudent(Long id) { return studentRepository.findById(id).orElseThrow(() -> new RuntimeException("Student not found")); }

    public Student createStudent(StudentDTO dto) {
        Student student = new Student();
        Member member = getMember(dto.getMemberId());
        student.setMember(member);
        student.setStudentCode(dto.getStudentCode());
        return studentRepository.save(student);
    }

    public Student updateStudent(Long id, StudentDTO dto) {
        Student student = getStudent(id);
        if (dto.getMemberId() != null) student.setMember(getMember(dto.getMemberId()));
        if (dto.getStudentCode() != null) student.setStudentCode(dto.getStudentCode());
        return studentRepository.save(student);
    }

    public void deleteStudent(Long id) { studentRepository.deleteById(id); }

    // --- Employee CRUD ---
    public List<Employee> getAllEmployees() { return employeeRepository.findAll(); }
    public Employee getEmployee(Long id) { return employeeRepository.findById(id).orElseThrow(() -> new RuntimeException("Employee not found")); }

    public Employee createEmployee(EmployeeDTO dto) {
        Employee employee = new Employee();
        Member member = getMember(dto.getMemberId());
        employee.setMember(member);
        updateEmployeeFromDTO(employee, dto);
        return employeeRepository.save(employee);
    }

    public Employee updateEmployee(Long id, EmployeeDTO dto) {
        Employee employee = getEmployee(id);
        if (dto.getMemberId() != null) employee.setMember(getMember(dto.getMemberId()));
        updateEmployeeFromDTO(employee, dto);
        return employeeRepository.save(employee);
    }

    public void deleteEmployee(Long id) { employeeRepository.deleteById(id); }

    private void updateEmployeeFromDTO(Employee employee, EmployeeDTO dto) {
        employee.setEmployeeCode(dto.getEmployeeCode());
        employee.setEmployeeRole(dto.getEmployeeRole());
        employee.setEmployeeType(dto.getEmployeeType());
        employee.setContractType(dto.getContractType());
        employee.setHireDate(dto.getHireDate());
        employee.setSalary(dto.getSalary());
        employee.setStatus(dto.getStatus());
    }

    // --- Course CRUD ---
    public List<Course> getAllCourses() { return courseRepository.findAll(); }
    public Course getCourse(Long id) { return courseRepository.findById(id).orElseThrow(() -> new RuntimeException("Course not found")); }

    public Course createCourse(CourseCrudDTO dto) {
        Course course = new Course();
        updateCourseFromDTO(course, dto);
        return courseRepository.save(course);
    }

    public Course updateCourse(Long id, CourseCrudDTO dto) {
        Course course = getCourse(id);
        updateCourseFromDTO(course, dto);
        return courseRepository.save(course);
    }

    public void deleteCourse(Long id) { courseRepository.deleteById(id); }

    private void updateCourseFromDTO(Course course, CourseCrudDTO dto) {
        if (dto.getProgramId() != null) {
             Program program = programRepository.findById(dto.getProgramId()).orElseThrow(() -> new RuntimeException("Program not found"));
             course.setProgram(program);
        }
        course.setCode(dto.getCode());
        course.setName(dto.getName());
        course.setCredits(dto.getCredits());
        course.setLevel(dto.getLevel());
    }

    // --- CourseGroup CRUD ---
    public List<CourseGroup> getAllCourseGroups() { return courseGroupRepository.findAll(); }
    public CourseGroup getCourseGroup(Long id) { return courseGroupRepository.findById(id).orElseThrow(() -> new RuntimeException("Group not found")); }

    public CourseGroup createCourseGroup(CourseGroupDTO dto) {
        CourseGroup group = new CourseGroup();
        updateCourseGroupFromDTO(group, dto);
        return courseGroupRepository.save(group);
    }

    public CourseGroup updateCourseGroup(Long id, CourseGroupDTO dto) {
        CourseGroup group = getCourseGroup(id);
        updateCourseGroupFromDTO(group, dto);
        return courseGroupRepository.save(group);
    }

    public void deleteCourseGroup(Long id) { courseGroupRepository.deleteById(id); }

    private void updateCourseGroupFromDTO(CourseGroup group, CourseGroupDTO dto) {
        if (dto.getCourseId() != null) group.setCourse(getCourse(dto.getCourseId()));
        if (dto.getPeriodId() != null) group.setPeriod(academicPeriodRepository.findById(dto.getPeriodId()).orElseThrow(() -> new RuntimeException("Period not found")));
        if (dto.getProfessorId() != null) group.setProfessor(getEmployee(dto.getProfessorId()));
        group.setGroupCode(dto.getGroupCode());
    }

    // --- Assignment CRUD ---
    public List<Assignment> getAllAssignments() { return assignmentRepository.findAll(); }
    public Assignment getAssignment(Long id) { return assignmentRepository.findById(id).orElseThrow(() -> new RuntimeException("Assignment not found")); }
    
    public Assignment createAssignment(AssignmentDTO dto) {
        Assignment assignment = new Assignment();
        updateAssignmentFromDTO(assignment, dto);
        return assignmentRepository.save(assignment);
    }

    public Assignment updateAssignment(Long id, AssignmentDTO dto) {
        Assignment assignment = getAssignment(id);
        updateAssignmentFromDTO(assignment, dto);
        return assignmentRepository.save(assignment);
    }

    public void deleteAssignment(Long id) { assignmentRepository.deleteById(id); }

    private void updateAssignmentFromDTO(Assignment assignment, AssignmentDTO dto) {
        if (dto.getCourseGroupId() != null) assignment.setCourseGroup(getCourseGroup(dto.getCourseGroupId()));
        assignment.setTitle(dto.getTitle());
        assignment.setDescription(dto.getDescription());
        assignment.setDueDate(dto.getDueDate());
        assignment.setMaxScore(dto.getMaxScore());
    }
}

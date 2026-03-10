package co.edu.konradlorenz.kapp.user.service;

import co.edu.konradlorenz.kapp.common.dto.*;
import co.edu.konradlorenz.kapp.common.exception.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.user.entity.*;
import co.edu.konradlorenz.kapp.user.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio de administración de usuarios.
 * Gestiona CRUD de personas, miembros, estudiantes y empleados.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserAdminService {

    private final PersonRepository personRepository;
    private final MemberRepository memberRepository;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    // ==================== PERSON CRUD ====================

    @Transactional(readOnly = true)
    public List<Person> getAllPeople() {
        return personRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Person getPerson(Long id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Person", "id", id));
    }

    public Person createPerson(PersonDTO dto) {
        Person person = new Person();
        updatePersonFromDTO(person, dto);
        person.setIsActive(true);
        return personRepository.save(person);
    }

    public Person updatePerson(Long id, PersonDTO dto) {
        Person person = getPerson(id);
        updatePersonFromDTO(person, dto);
        return personRepository.save(person);
    }

    public void deletePerson(Long id) {
        personRepository.deleteById(id);
    }

    private void updatePersonFromDTO(Person person, PersonDTO dto) {
        person.setIdentificationNumber(dto.getIdentificationNumber());
        person.setIdentificationType(dto.getIdentificationType());
        person.setFirstName(dto.getFirstName());
        person.setLastName(dto.getLastName());
        person.setPhone(dto.getPhone());
        person.setHomeAddress(dto.getHomeAddress());
        person.setBirthDate(dto.getBirthDate());
    }

    // ==================== MEMBER CRUD ====================

    @Transactional(readOnly = true)
    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Member getMember(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));
    }

    public Member getMemberByEmail(String email) {
        return memberRepository.findByUniversityEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "email", email));
    }

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
        if (dto.getPersonId() != null)
            member.setPerson(getPerson(dto.getPersonId()));
        if (dto.getUniversityCode() != null)
            member.setUniversityCode(dto.getUniversityCode());
        if (dto.getUniversityEmail() != null)
            member.setUniversityEmail(dto.getUniversityEmail());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            member.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }
        return memberRepository.save(member);
    }

    public void deleteMember(Long id) {
        memberRepository.deleteById(id);
    }

    // ==================== STUDENT CRUD ====================

    @Transactional(readOnly = true)
    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Student getStudent(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student", "id", id));
    }

    public Student getStudentByEmail(String email) {
        Member member = getMemberByEmail(email);
        return studentRepository.findByMember(member)
                .orElseThrow(() -> new ResourceNotFoundException("Student", "email", email));
    }

    public Student createStudent(StudentDTO dto) {
        Student student = new Student();
        Member member = getMember(dto.getMemberId());
        student.setMember(member);
        student.setStudentCode(dto.getStudentCode());
        return studentRepository.save(student);
    }

    public Student updateStudent(Long id, StudentDTO dto) {
        Student student = getStudent(id);
        if (dto.getMemberId() != null)
            student.setMember(getMember(dto.getMemberId()));
        if (dto.getStudentCode() != null)
            student.setStudentCode(dto.getStudentCode());
        return studentRepository.save(student);
    }

    public void deleteStudent(Long id) {
        studentRepository.deleteById(id);
    }

    // ==================== EMPLOYEE CRUD ====================

    @Transactional(readOnly = true)
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Employee getEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));
    }

    public Employee getEmployeeByEmail(String email) {
        Member member = getMemberByEmail(email);
        return employeeRepository.findByMember(member)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "email", email));
    }

    public Employee createEmployee(EmployeeDTO dto) {
        Employee employee = new Employee();
        Member member = getMember(dto.getMemberId());
        employee.setMember(member);
        updateEmployeeFromDTO(employee, dto);
        return employeeRepository.save(employee);
    }

    public Employee updateEmployee(Long id, EmployeeDTO dto) {
        Employee employee = getEmployee(id);
        if (dto.getMemberId() != null)
            employee.setMember(getMember(dto.getMemberId()));
        updateEmployeeFromDTO(employee, dto);
        return employeeRepository.save(employee);
    }

    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }

    private void updateEmployeeFromDTO(Employee employee, EmployeeDTO dto) {
        employee.setEmployeeCode(dto.getEmployeeCode());
        employee.setEmployeeRole(dto.getEmployeeRole());
        employee.setEmployeeType(dto.getEmployeeType());
        employee.setContractType(dto.getContractType());
        employee.setHireDate(dto.getHireDate());
        employee.setSalary(dto.getSalary());
        employee.setStatus(dto.getStatus());
    }
}

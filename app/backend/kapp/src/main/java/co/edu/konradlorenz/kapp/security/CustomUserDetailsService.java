package co.edu.konradlorenz.kapp.security;

import co.edu.konradlorenz.kapp.entity.Employee;
import co.edu.konradlorenz.kapp.entity.Member;
import co.edu.konradlorenz.kapp.entity.Student;
import co.edu.konradlorenz.kapp.repository.EmployeeRepository;
import co.edu.konradlorenz.kapp.repository.MemberRepository;
import co.edu.konradlorenz.kapp.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Member member = memberRepository.findByUniversityEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        List<GrantedAuthority> authorities = new ArrayList<>();

        // Check if Student
        Optional<Student> student = studentRepository.findByMember(member);
        if (student.isPresent()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_STUDENT"));
        }

        // Check if Employee
        Optional<Employee> employee = employeeRepository.findByMember(member);
        if (employee.isPresent()) {
            String role = employee.get().getEmployeeRole();
            if ("PROFESOR_PLANTA".equals(role) || "PROFESOR_CATEDRA".equals(role)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_PROFESSOR"));
            } else if ("ADMINISTRADOR_SISTEMA".equals(role)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
        }

        return new User(member.getUniversityEmail(), member.getPasswordHash(), authorities);
    }
}

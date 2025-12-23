package co.edu.konradlorenz.kapp.auth.security;

import co.edu.konradlorenz.kapp.auth.entity.Employee;
import co.edu.konradlorenz.kapp.auth.entity.Member;
import co.edu.konradlorenz.kapp.auth.entity.Student;
import co.edu.konradlorenz.kapp.auth.repository.EmployeeRepository;
import co.edu.konradlorenz.kapp.auth.repository.MemberRepository;
import co.edu.konradlorenz.kapp.auth.repository.StudentRepository;
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

/**
 * Servicio personalizado para cargar detalles de usuario.
 * Determina los roles basándose en si es estudiante o empleado.
 */
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
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));

        List<GrantedAuthority> authorities = new ArrayList<>();

        // Verificar si es Estudiante
        Optional<Student> student = studentRepository.findByMember(member);
        if (student.isPresent()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_STUDENT"));
        }

        // Verificar si es Empleado
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

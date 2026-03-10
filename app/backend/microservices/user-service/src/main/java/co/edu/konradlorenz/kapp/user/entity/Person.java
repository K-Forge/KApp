package co.edu.konradlorenz.kapp.user.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Date;

/**
 * Entidad Person - Información personal básica.
 */
@Entity
@Table(name = "person")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "person_id")
    private Long id;

    @Column(name = "identification_number", nullable = false, unique = true)
    private Integer identificationNumber;

    @Column(name = "identification_type", nullable = false)
    private String identificationType;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String phone;

    @Column(name = "home_address")
    private String homeAddress;

    @Column(name = "birth_date")
    private Date birthDate;

    private String avatar;

    @Column(name = "is_active")
    private Boolean isActive;
}

package co.edu.konradlorenz.kapp.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "employee")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id")
    private Long id;

    @OneToOne
    @JoinColumn(name = "member_id", referencedColumnName = "member_id", nullable = false)
    private Member member;

    @Column(name = "employee_code", nullable = false, unique = true)
    private String employeeCode;

    @Column(name = "employee_role", nullable = false)
    private String employeeRole; // Enum mapped as String

    @Column(name = "employee_type")
    private String employeeType;

    @Column(name = "contract_type")
    private String contractType;

    @Column(name = "hire_date")
    private java.util.Date hireDate;

    @Column(name = "salary")
    private Double salary;

    @Column(name = "status")
    private String status;
}

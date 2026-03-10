package co.edu.konradlorenz.kapp.course.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Entidad StudentCourse - Matrícula de estudiante en un grupo.
 */
@Entity
@Table(name = "student_course")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "student_course_id")
    private Long id;

    // ID del estudiante (referencia al user-service)
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private CourseGroup group;

    @Column(nullable = false)
    private String status;

    @Column(name = "final_grade")
    private Integer finalGrade;
}

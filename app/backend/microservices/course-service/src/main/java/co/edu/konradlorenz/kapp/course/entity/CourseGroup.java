package co.edu.konradlorenz.kapp.course.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Entidad CourseGroup - Grupo de un curso específico.
 */
@Entity
@Table(name = "course_group")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_group_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne
    @JoinColumn(name = "period_id")
    private AcademicPeriod period;

    // ID del profesor (referencia al user-service)
    @Column(name = "professor_id")
    private Long professorId;

    @Column(name = "group_code", nullable = false)
    private String groupCode;
}

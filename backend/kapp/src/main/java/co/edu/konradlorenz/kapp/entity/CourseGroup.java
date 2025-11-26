package co.edu.konradlorenz.kapp.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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

    @ManyToOne
    @JoinColumn(name = "professor_id")
    private Employee professor;

    @Column(name = "group_code", nullable = false)
    private String groupCode;
}

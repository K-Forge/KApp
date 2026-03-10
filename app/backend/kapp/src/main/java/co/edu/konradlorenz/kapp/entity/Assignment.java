package co.edu.konradlorenz.kapp.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Date;

@Entity
@Table(name = "assignment")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "course_group_id", nullable = false)
    private CourseGroup courseGroup;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "due_date")
    private Date dueDate;

    @Column(name = "max_score")
    private Double maxScore;
}

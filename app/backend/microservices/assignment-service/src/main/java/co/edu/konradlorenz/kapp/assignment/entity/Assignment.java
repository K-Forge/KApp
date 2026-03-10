package co.edu.konradlorenz.kapp.assignment.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Date;

/**
 * Entidad Assignment - Tarea asignada a un grupo.
 */
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

    // ID del grupo de curso (referencia al course-service)
    @Column(name = "course_group_id", nullable = false)
    private Long courseGroupId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "due_date")
    private Date dueDate;

    @Column(name = "max_score")
    private Double maxScore;
}

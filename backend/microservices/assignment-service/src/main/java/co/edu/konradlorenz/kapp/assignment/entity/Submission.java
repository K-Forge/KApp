package co.edu.konradlorenz.kapp.assignment.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Date;

/**
 * Entidad Submission - Entrega de tarea por un estudiante.
 */
@Entity
@Table(name = "submission")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "submission_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    // ID del estudiante (referencia al user-service)
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "submission_date")
    private Date submissionDate;

    @Column(name = "content_url")
    private String contentUrl;

    private Double grade;

    private String feedback;
}

package co.edu.konradlorenz.kapp.service;

import co.edu.konradlorenz.kapp.dto.AssignmentDTO;
import co.edu.konradlorenz.kapp.dto.CourseDTO;
import co.edu.konradlorenz.kapp.dto.SubmissionDTO;
import co.edu.konradlorenz.kapp.entity.*;
import co.edu.konradlorenz.kapp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final MemberRepository memberRepository;
    private final StudentRepository studentRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;

    private Student getStudent(String email) {
        Member member = memberRepository.findByUniversityEmail(email)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        return studentRepository.findByMember(member)
                .orElseThrow(() -> new RuntimeException("Student not found"));
    }

    @Transactional(readOnly = true)
    public List<CourseDTO> getEnrolledCourses(String email) {
        Student student = getStudent(email);
        List<StudentCourse> enrollments = studentCourseRepository.findByStudentId(student.getId());

        return enrollments.stream().map(sc -> {
            CourseGroup group = sc.getGroup();
            CourseDTO dto = new CourseDTO();
            dto.setCourseGroupId(group.getId());
            dto.setCourseName(group.getCourse().getName());
            dto.setCourseCode(group.getCourse().getCode());
            dto.setGroupCode(group.getGroupCode());
            if (group.getProfessor() != null && group.getProfessor().getMember() != null && group.getProfessor().getMember().getPerson() != null) {
                dto.setProfessorName(group.getProfessor().getMember().getPerson().getFirstName() + " " +
                        group.getProfessor().getMember().getPerson().getLastName());
            } else {
                dto.setProfessorName("TBA");
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AssignmentDTO> getPendingAssignments(String email) {
        Student student = getStudent(email);
        List<StudentCourse> enrollments = studentCourseRepository.findByStudentId(student.getId());
        List<AssignmentDTO> pending = new ArrayList<>();

        for (StudentCourse sc : enrollments) {
            List<Assignment> assignments = assignmentRepository.findByCourseGroupId(sc.getGroup().getId());
            for (Assignment a : assignments) {
                boolean submitted = submissionRepository.findByAssignmentIdAndStudentId(a.getId(), student.getId()).isPresent();
                if (!submitted) {
                    AssignmentDTO dto = new AssignmentDTO();
                    dto.setId(a.getId());
                    dto.setCourseGroupId(a.getCourseGroup().getId());
                    dto.setTitle(a.getTitle());
                    dto.setDescription(a.getDescription());
                    dto.setDueDate(a.getDueDate());
                    dto.setMaxScore(a.getMaxScore());
                    pending.add(dto);
                }
            }
        }
        return pending;
    }

    @Transactional(readOnly = true)
    public List<AssignmentDTO> getSubmittedAssignments(String email) {
        Student student = getStudent(email);
        List<StudentCourse> enrollments = studentCourseRepository.findByStudentId(student.getId());
        List<AssignmentDTO> submittedList = new ArrayList<>();

        for (StudentCourse sc : enrollments) {
            List<Assignment> assignments = assignmentRepository.findByCourseGroupId(sc.getGroup().getId());
            for (Assignment a : assignments) {
                boolean submitted = submissionRepository.findByAssignmentIdAndStudentId(a.getId(), student.getId()).isPresent();
                if (submitted) {
                    AssignmentDTO dto = new AssignmentDTO();
                    dto.setId(a.getId());
                    dto.setCourseGroupId(a.getCourseGroup().getId());
                    dto.setTitle(a.getTitle());
                    dto.setDescription(a.getDescription());
                    dto.setDueDate(a.getDueDate());
                    dto.setMaxScore(a.getMaxScore());
                    submittedList.add(dto);
                }
            }
        }
        return submittedList;
    }

    @Transactional
    public SubmissionDTO submitAssignment(String email, Long assignmentId, String contentUrl) {
        Student student = getStudent(email);
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Assignment not found"));

        if (submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId()).isPresent()) {
            throw new RuntimeException("Assignment already submitted");
        }

        Submission submission = new Submission();
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setSubmissionDate(new Date());
        submission.setContentUrl(contentUrl);
        
        submission = submissionRepository.save(submission);

        SubmissionDTO dto = new SubmissionDTO();
        dto.setId(submission.getId());
        dto.setAssignmentId(assignment.getId());
        dto.setStudentId(student.getId());
        dto.setSubmissionDate(submission.getSubmissionDate());
        dto.setContentUrl(submission.getContentUrl());
        return dto;
    }
}

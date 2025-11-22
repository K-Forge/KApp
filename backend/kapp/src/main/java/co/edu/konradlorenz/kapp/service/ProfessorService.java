package co.edu.konradlorenz.kapp.service;

import co.edu.konradlorenz.kapp.dto.AssignmentDTO;
import co.edu.konradlorenz.kapp.dto.CourseDTO;
import co.edu.konradlorenz.kapp.dto.StudentSummaryDTO;
import co.edu.konradlorenz.kapp.dto.SubmissionDTO;
import co.edu.konradlorenz.kapp.entity.*;
import co.edu.konradlorenz.kapp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfessorService {

    private final MemberRepository memberRepository;
    private final EmployeeRepository employeeRepository;
    private final CourseGroupRepository courseGroupRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentCourseRepository studentCourseRepository;

    private Employee getProfessor(String email) {
        Member member = memberRepository.findByUniversityEmail(email)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        return employeeRepository.findByMember(member)
                .orElseThrow(() -> new RuntimeException("Professor not found"));
    }

    @Transactional(readOnly = true)
    public List<CourseDTO> getTeachingCourses(String email) {
        Employee professor = getProfessor(email);
        List<CourseGroup> groups = courseGroupRepository.findByProfessorId(professor.getId());

        return groups.stream().map(group -> {
            CourseDTO dto = new CourseDTO();
            dto.setCourseGroupId(group.getId());
            dto.setCourseName(group.getCourse().getName());
            dto.setCourseCode(group.getCourse().getCode());
            dto.setGroupCode(group.getGroupCode());
            dto.setProfessorName(professor.getMember().getPerson().getFirstName() + " " +
                    professor.getMember().getPerson().getLastName());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public AssignmentDTO createAssignment(String email, AssignmentDTO assignmentDTO) {
        Employee professor = getProfessor(email);
        CourseGroup group = courseGroupRepository.findById(assignmentDTO.getCourseGroupId())
                .orElseThrow(() -> new RuntimeException("Course Group not found"));

        if (!group.getProfessor().getId().equals(professor.getId())) {
            throw new RuntimeException("You are not the professor of this course");
        }

        Assignment assignment = new Assignment();
        assignment.setCourseGroup(group);
        assignment.setTitle(assignmentDTO.getTitle());
        assignment.setDescription(assignmentDTO.getDescription());
        assignment.setDueDate(assignmentDTO.getDueDate());
        assignment.setMaxScore(assignmentDTO.getMaxScore());

        assignment = assignmentRepository.save(assignment);
        assignmentDTO.setId(assignment.getId());
        return assignmentDTO;
    }

    @Transactional
    public SubmissionDTO gradeSubmission(Long submissionId, Double grade, String feedback) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        submission.setGrade(grade);
        submission.setFeedback(feedback);
        submission = submissionRepository.save(submission);

        SubmissionDTO dto = new SubmissionDTO();
        dto.setId(submission.getId());
        dto.setAssignmentId(submission.getAssignment().getId());
        dto.setStudentId(submission.getStudent().getId());
        dto.setGrade(submission.getGrade());
        dto.setFeedback(submission.getFeedback());
        return dto;
    }

    @Transactional(readOnly = true)
    public List<StudentSummaryDTO> getGroupStudents(String email, Long groupId) {
        Employee professor = getProfessor(email);
        CourseGroup group = courseGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Course Group not found"));

        if (!group.getProfessor().getId().equals(professor.getId())) {
            throw new RuntimeException("You are not the professor of this course");
        }

        List<StudentCourse> enrollments = studentCourseRepository.findByGroupId(groupId);
        return enrollments.stream().map(enrollment -> {
            Student student = enrollment.getStudent();
            Person person = student.getMember().getPerson();
            
            StudentSummaryDTO dto = new StudentSummaryDTO();
            dto.setId(student.getId());
            dto.setFullName(person.getFirstName() + " " + person.getLastName());
            dto.setStudentCode(student.getStudentCode());
            dto.setEmail(student.getMember().getUniversityEmail());
            return dto;
        }).collect(Collectors.toList());
    }
}

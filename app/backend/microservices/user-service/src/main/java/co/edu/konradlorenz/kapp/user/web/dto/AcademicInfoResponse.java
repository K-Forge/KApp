package co.edu.konradlorenz.kapp.user.web.dto;

import co.edu.konradlorenz.kapp.user.sinu.SinuStudent;

/**
 * The {@code AcademicInfo} schema: a student's program, pensum and level, read from SINU for the
 * student themselves. Never stored, and never shown to anybody else.
 */
public record AcademicInfoResponse(String programCode, String programName, String pensumCode, int currentLevel) {

    public static AcademicInfoResponse from(SinuStudent student) {
        return new AcademicInfoResponse(student.programCode(), student.programName(), student.pensumCode(),
                student.currentLevel());
    }
}

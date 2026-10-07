package co.edu.konradlorenz.kapp.schedule.web;

import co.edu.konradlorenz.kapp.common.security.CurrentUser;
import co.edu.konradlorenz.kapp.common.security.KappRoles;
import co.edu.konradlorenz.kapp.schedule.sinu.SinuPerson;

/**
 * Whose timetable a request reads: always the caller's own, from the validated token. A student's is
 * the sections they take, a professor's the sections they teach; a token with both profile roles is
 * read as the student's, which is the timetable a person who studies and teaches checks most.
 */
final class Callers {

    private Callers() {
    }

    static SinuPerson person() {
        SinuPerson.Role role = CurrentUser.hasRole(KappRoles.STUDENT)
                ? SinuPerson.Role.STUDENT
                : SinuPerson.Role.PROFESSOR;
        return new SinuPerson(CurrentUser.id(), CurrentUser.email(), role);
    }
}

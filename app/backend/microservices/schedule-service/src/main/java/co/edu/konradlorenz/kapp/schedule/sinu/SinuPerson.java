package co.edu.konradlorenz.kapp.schedule.sinu;

/**
 * Whose timetable is read: the KApp user, the institutional e-mail SINU knows them by, and whether
 * their timetable is the sections they take or the ones they teach.
 */
public record SinuPerson(String userId, String email, Role role) {

    public enum Role {
        STUDENT,
        PROFESSOR
    }
}

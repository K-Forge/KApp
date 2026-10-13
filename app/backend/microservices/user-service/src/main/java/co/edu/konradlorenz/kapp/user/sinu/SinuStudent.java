package co.edu.konradlorenz.kapp.user.sinu;

/** What SINU says a student follows: their program, the version of its pensum, and their level. */
public record SinuStudent(String programCode, String programName, String pensumCode, int currentLevel) {
}

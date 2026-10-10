package co.edu.konradlorenz.kapp.semaphore.sinu;

/**
 * What SINU says a student follows: the program, the version of its pensum, and the level they are
 * in.
 */
public record SinuStudent(String programCode, String pensumCode, int currentLevel) {
}

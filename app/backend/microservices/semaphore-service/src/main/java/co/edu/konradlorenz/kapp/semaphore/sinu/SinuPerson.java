package co.edu.konradlorenz.kapp.semaphore.sinu;

/** Whose record is read: the KApp user, and the institutional e-mail SINU knows them by. */
public record SinuPerson(String userId, String email) {
}

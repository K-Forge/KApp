package co.edu.konradlorenz.kapp.user.sinu;

import java.util.Optional;

/**
 * SINU, as this service sees it: where a student's program, pensum and level come from, read for the
 * student themselves when they ask for their own profile, and never stored.
 *
 * <p>The university decides how SINU is opened, and each way is an adapter behind this port. Until
 * then the {@code fake} adapter serves the invented student of {@code docs/api/sinu/}.
 */
public interface SinuStudentPort {

    /**
     * @param email the institutional e-mail SINU knows the person by
     * @return the program, pensum and level SINU has them on; empty for someone who is not a student
     */
    Optional<SinuStudent> student(String userId, String email);
}

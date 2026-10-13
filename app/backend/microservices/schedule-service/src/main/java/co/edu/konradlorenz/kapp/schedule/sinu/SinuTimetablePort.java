package co.edu.konradlorenz.kapp.schedule.sinu;

import co.edu.konradlorenz.kapp.common.academic.AcademicPeriod;
import co.edu.konradlorenz.kapp.schedule.domain.Source;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * SINU, as this service sees it: the university's academic system, read and never written.
 *
 * <p>The university decides how SINU is opened - a web service, a database view or a file exported
 * every night - and each of those is an adapter behind this port. Until then the {@code fake} adapter
 * serves invented timetables of the same shape ({@code docs/api/sinu/}). The shape and the fields are
 * the ones {@code docs/api/sinu/README.md} asks the university for.
 */
public interface SinuTimetablePort {

    /** What the answers are: SINU's own, or the test adapter's invented ones. */
    Source source();

    /** The academic period SINU is in on that date. */
    AcademicPeriod periodOn(LocalDate date);

    /** The periods SINU has a timetable of this person for, in any order. */
    List<AcademicPeriod> periods(SinuPerson person);

    /** The person's timetable in that period, as SINU has it; empty when it has none. */
    Optional<SinuTimetable> timetable(SinuPerson person, AcademicPeriod period);
}

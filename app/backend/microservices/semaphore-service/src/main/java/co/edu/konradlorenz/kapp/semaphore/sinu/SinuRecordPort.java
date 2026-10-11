package co.edu.konradlorenz.kapp.semaphore.sinu;

import co.edu.konradlorenz.kapp.common.academic.AcademicPeriod;
import co.edu.konradlorenz.kapp.semaphore.domain.Source;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * SINU, as this service sees it: the university's academic record, read and never written.
 *
 * <p>The university decides how SINU is opened - a web service, a database view or a file exported
 * every night - and each of those is an adapter behind this port. Until then the {@code fake} adapter
 * serves invented data of the same shape ({@code docs/api/sinu/}). The fields are the ones
 * {@code docs/api/sinu/README.md} asks the university for.
 *
 * <p>The catalog is not read through here yet: the published plans this service loads are both the
 * backup of SINU's catalog and the test SINU's.
 */
public interface SinuRecordPort {

    /** What the answers are: SINU's own, or the test adapter's invented ones. */
    Source source();

    /** The academic period SINU is in on that date. */
    AcademicPeriod periodOn(LocalDate date);

    /** The program, pensum and level SINU has the person on; empty for someone who is not a student. */
    Optional<SinuStudent> student(SinuPerson person);

    /** Every course of the person's record SINU has, in any order. */
    List<SinuRecord> records(SinuPerson person);

    /** The courses SINU offers in that period to fill the pensum's elective slots. */
    List<SinuElectiveOffering> electiveBank(String pensumCode, AcademicPeriod period);
}

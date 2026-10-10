package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.semaphore.domain.CourseStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.Pensum;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumCourse;
import co.edu.konradlorenz.kapp.semaphore.domain.Semaphore;
import co.edu.konradlorenz.kapp.semaphore.domain.SemaphoreEntry;
import co.edu.konradlorenz.kapp.semaphore.repository.PensumRepository;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuPerson;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuRecordPort;
import co.edu.konradlorenz.kapp.semaphore.web.dto.AreaProgressDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumCourseDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.ProgressSummaryDto;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The student's semáforo, read from SINU and laid over their pensum, with what is derived from it:
 * the summary and the courses the prerequisites allow next. Read-only: SINU is where courses are
 * taken, and nothing here is stored.
 *
 * <p>Plans are deliberately not this class's concern - see {@link AcademicPlanService}. Eligibility
 * is computed from what SINU says was passed and never reads a plan, so a student cannot unlock a
 * course by dragging it into an earlier semester.
 */
@Service
public class SemaphoreService {

    private final StudentRecordReader reader;
    private final SinuRecordPort sinu;
    private final PensumRepository pensums;
    private final PrerequisiteWalker prerequisites;

    public SemaphoreService(StudentRecordReader reader, SinuRecordPort sinu, PensumRepository pensums,
                            PrerequisiteWalker prerequisites) {
        this.reader = reader;
        this.sinu = sinu;
        this.pensums = pensums;
        this.prerequisites = prerequisites;
    }

    /**
     * @throws ResourceNotFoundException (404) when SINU has no program for the caller - someone who
     *                                    is not a student, or not yet one - or their pensum is not in
     *                                    the catalog
     */
    public Semaphore semaphore(SinuPerson person) {
        StudentRecordReader.Reading reading = reader.read(person)
                .orElseThrow(() -> new ResourceNotFoundException("No program in SINU for user " + person.userId()));
        Pensum pensum = requirePensum(reading.student().pensumCode());
        return new Semaphore(person.userId(), reading.student().programCode(), pensum.pensumCode(),
                reading.student().currentLevel(), sinu.source(), reading.readAt(),
                SemaphoreBuilder.entries(pensum, reading.records()));
    }

    /**
     * Credit totals of the semáforo, recomputed on every call: {@code PASSED} counts as passed,
     * {@code IN_PROGRESS} as in progress, and everything else - pending, lost, postponed - as
     * remaining. The level is the one SINU has the student in.
     */
    public ProgressSummaryDto summary(SinuPerson person) {
        Semaphore semaphore = semaphore(person);
        Pensum pensum = requirePensum(semaphore.pensumCode());
        Map<String, PensumCourse> items = pensum.byPensumItemCode();

        int creditsPassed = 0;
        int creditsInProgress = 0;
        Map<String, Integer> passedByArea = new LinkedHashMap<>();
        pensum.areaCodes().forEach(code -> passedByArea.put(code, 0));
        for (SemaphoreEntry entry : semaphore.courses()) {
            PensumCourse item = items.get(entry.pensumItemCode());
            if (entry.status() == CourseStatus.PASSED) {
                creditsPassed += item.credits();
                passedByArea.merge(item.area(), item.credits(), Integer::sum);
            } else if (entry.status() == CourseStatus.IN_PROGRESS) {
                creditsInProgress += item.credits();
            }
        }

        int totalCredits = pensum.totalCredits();
        double percent = totalCredits == 0 ? 0.0 : Math.round(creditsPassed * 1000.0 / totalCredits) / 10.0;
        List<AreaProgressDto> byArea = pensum.areas().stream()
                .map(area -> new AreaProgressDto(area.code(), passedByArea.getOrDefault(area.code(), 0), area.credits()))
                .toList();
        return new ProgressSummaryDto(creditsPassed, creditsInProgress,
                Math.max(0, totalCredits - creditsPassed - creditsInProgress), totalCredits, percent, byArea,
                semaphore.currentLevel());
    }

    /**
     * The items still {@code PENDING} whose prerequisites are all {@code PASSED}, in pensum form and
     * pensum order. Lost and postponed items are taken again, not for the first time, and are not
     * listed.
     */
    public List<PensumCourseDto> eligible(SinuPerson person) {
        Semaphore semaphore = semaphore(person);
        Pensum pensum = requirePensum(semaphore.pensumCode());
        Map<String, SemaphoreEntry> byItem = semaphore.byPensumItemCode();
        return pensum.coursesInDisplayOrder().stream()
                .filter(item -> byItem.get(item.pensumItemCode()).status() == CourseStatus.PENDING)
                .filter(item -> prerequisites.allPrerequisitesPassed(item, byItem))
                .map(PensumCourseDto::from)
                .toList();
    }

    private Pensum requirePensum(String pensumCode) {
        return pensums.findById(pensumCode)
                .orElseThrow(() -> new ResourceNotFoundException("Pensum", pensumCode));
    }
}

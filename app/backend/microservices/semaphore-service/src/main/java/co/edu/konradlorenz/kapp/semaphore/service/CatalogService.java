package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.common.academic.AcademicPeriod;
import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.semaphore.domain.Pensum;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.Program;
import co.edu.konradlorenz.kapp.semaphore.repository.PensumRepository;
import co.edu.konradlorenz.kapp.semaphore.repository.ProgramRepository;
import co.edu.konradlorenz.kapp.semaphore.web.dto.ElectiveOfferingDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumCourseDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumSummary;
import co.edu.konradlorenz.kapp.semaphore.web.dto.ProgramResponse;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Reads the academic catalog: programs, pensums and each semester's elective bank.
 *
 * <p>Read-only. The catalog is SINU's; the published plans this service loads are its backup, and
 * the only write it takes is the import of that backup ({@code PensumCsvImporter}).
 */
@Service
public class CatalogService {

    private final ProgramRepository programs;
    private final PensumRepository pensums;
    private final ActivePensumResolver activePensum;
    private final ElectiveBank electives;

    public CatalogService(ProgramRepository programs, PensumRepository pensums,
                          ActivePensumResolver activePensum, ElectiveBank electives) {
        this.programs = programs;
        this.pensums = pensums;
        this.activePensum = activePensum;
        this.electives = electives;
    }

    public List<ProgramResponse> listPrograms() {
        return programs.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Every pensum, newest-looking first: active ones before drafts, then by code.
     *
     * <p>Summaries, not documents - see {@link PensumSummary}. The whole catalogue is a few
     * dozen rows, so this is deliberately not paginated: a picker that pages is a picker nobody
     * can use, and the day this needs pages is the day it needs a search box instead.
     */
    public List<PensumSummary> listPensums() {
        return pensums.findAll().stream()
                .sorted(Comparator
                        .comparing((Pensum c) -> c.status() != PensumStatus.ACTIVE)
                        .thenComparing(Pensum::programName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(Pensum::pensumCode))
                .map(PensumSummary::from)
                .toList();
    }

    public ProgramResponse getProgram(String programCode) {
        Program program = programs.findById(programCode)
                .orElseThrow(() -> new ResourceNotFoundException("Program", programCode));
        return toResponse(program);
    }

    private ProgramResponse toResponse(Program program) {
        String activePensumCode = activePensum.findActive(program.code())
                .map(Pensum::pensumCode)
                .orElse(null);
        return ProgramResponse.of(program, activePensumCode);
    }

    public PensumDto getPensum(String pensumCode) {
        return PensumDto.from(requirePensum(pensumCode));
    }

    public List<PensumCourseDto> listPensumCourses(String pensumCode, Integer level,
                                                   String area, Boolean isElectiveSlot) {
        Pensum pensum = requirePensum(pensumCode);
        return pensum.coursesInDisplayOrder().stream()
                .filter(course -> level == null || course.level() == level)
                .filter(course -> area == null || area.equals(course.area()))
                .filter(course -> isElectiveSlot == null || isElectiveSlot == course.electiveSlot())
                .map(PensumCourseDto::from)
                .toList();
    }

    /**
     * The courses SINU offers in that period for the pensum's elective slots, read from SINU every
     * time. A period SINU has not published answers an empty list, not an error.
     *
     * @param period null for the current one
     * @throws ResourceNotFoundException (404) if the pensum does not exist
     */
    public List<ElectiveOfferingDto> listElectives(String pensumCode, String period) {
        Pensum pensum = requirePensum(pensumCode);
        return electives.offerings(pensum.pensumCode(), period == null ? null : AcademicPeriod.parse(period)).stream()
                .map(ElectiveOfferingDto::from)
                .toList();
    }

    private Pensum requirePensum(String pensumCode) {
        return pensums.findById(pensumCode)
                .orElseThrow(() -> new ResourceNotFoundException("Pensum", pensumCode));
    }
}

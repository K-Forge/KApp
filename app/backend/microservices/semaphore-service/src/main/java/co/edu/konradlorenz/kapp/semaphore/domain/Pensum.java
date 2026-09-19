package co.edu.konradlorenz.kapp.semaphore.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A pensum - the whole degree plan a semaforo is drawn from.
 *
 * <h2>Why this lives beside student progress</h2>
 * The pensum IS the semaforo. What a student opens is the pensum grid coloured by
 * their own progress, so splitting the two would make the most-used screen in the app a
 * two-service read on every open.
 *
 * <h2>Declared totals are not recomputed</h2>
 * {@code totalCredits} and {@code totalHours} are what the printed plan declares. They are
 * deliberately not derived from {@link #courses()}: when the seeded items disagree with the
 * printed plan, that disagreement is a finding to report, not an arithmetic accident to
 * hide by recomputing. {@code SeedTotalsTest} asserts the comparison and
 * {@code NEEDS_VERIFICATION.md} records every mismatch.
 *
 * @param pensumCode   institutional code of the plan, unique across the catalogue
 * @param programCode  code of the program this plan belongs to
 * @param programName  denormalised program name, so rendering the grid needs no join
 * @param faculty      denormalised faculty name
 * @param reform       name of the curricular reform this plan belongs to
 * @param status       lifecycle state; only ACTIVE plans are pinned to new students
 * @param totalCredits credits the printed plan declares
 * @param totalHours   <strong>weekly</strong> hours the printed plan declares; whole or half,
 *                     because the items it adds up can be (see {@link WeeklyHours})
 * @param levels       number of levels (semesters) - the number of grid columns
 * @param areas        knowledge areas in display order - the grid rows
 * @param courses      every item of the plan, fixed courses and elective slots alike
 */
@Document(collection = "pensums")
public record Pensum(
        @Id String pensumCode,
        String programCode,
        String programName,
        String faculty,
        String reform,
        PensumStatus status,
        int totalCredits,
        @JsonSerialize(using = WeeklyHours.Serializer.class) double totalHours,
        int levels,
        List<PensumArea> areas,
        List<PensumCourse> courses
) {

    public Pensum {
        areas = areas == null ? List.of() : List.copyOf(areas);
        courses = courses == null ? List.of() : List.copyOf(courses);
    }

    /**
     * @return the item addressed by {@code identifier}, which is a course {@code code} for
     *         a fixed course and a {@code pensumItemCode} for an elective slot
     */
    public Optional<PensumCourse> findByAddressableCode(String identifier) {
        return courses.stream()
                .filter(c -> c.addressableCode().equals(identifier))
                .findFirst();
    }

    public Optional<PensumCourse> findByPensumItemCode(String pensumItemCode) {
        return courses.stream()
                .filter(c -> c.pensumItemCode().equals(pensumItemCode))
                .findFirst();
    }

    /** @return items keyed by {@code pensumItemCode}, which every item has */
    public Map<String, PensumCourse> byPensumItemCode() {
        Map<String, PensumCourse> index = new LinkedHashMap<>();
        courses.forEach(c -> index.put(c.pensumItemCode(), c));
        return index;
    }

    /** @return items keyed by course {@code code}, skipping elective slots, which have none */
    public Map<String, PensumCourse> byCourseCode() {
        Map<String, PensumCourse> index = new LinkedHashMap<>();
        courses.stream()
                .filter(c -> c.code() != null)
                .forEach(c -> index.put(c.code(), c));
        return index;
    }

    /**
     * @return the items in the order the API always returns them: level, then name.
     *         Fixed everywhere so a client can rely on it without sorting again.
     */
    public List<PensumCourse> coursesInDisplayOrder() {
        return courses.stream()
                .sorted(Comparator.comparingInt(PensumCourse::level)
                        .thenComparing(PensumCourse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<String> areaCodes() {
        return areas.stream().map(PensumArea::code).toList();
    }
}

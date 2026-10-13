package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.semaphore.domain.CourseStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.Pensum;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumCourse;
import co.edu.konradlorenz.kapp.semaphore.domain.SemaphoreEntry;
import co.edu.konradlorenz.kapp.semaphore.sinu.SinuRecord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A student's record laid over their pensum: one entry per item, in the pensum's order.
 *
 * <ul>
 *   <li><b>A fixed course</b> takes the record with its {@code sinuCode}. An item whose SINU code is
 *       not known has nothing to match, and stays {@code PENDING}.</li>
 *   <li><b>A course taken more than once</b> - lost, then passed - shows its latest attempt: the
 *       most recent period, and within one period the furthest along.</li>
 *   <li><b>An elective slot</b> takes, first, a record under the slot's own code: a real report lists
 *       the elective that way ({@code 59098 ELECTIVA VI}). Then, in level order, the records that
 *       match no item of the pensum fill the slots still empty, oldest first: an elective taken
 *       under the code of the course itself. How SINU records electives is a question for the
 *       university (docs/api/sinu/README.md); this serves both forms until it answers.</li>
 *   <li><b>Anything else SINU has</b> - a record with no slot left for it - is not shown: the
 *       semáforo mirrors the pensum one to one.</li>
 * </ul>
 */
public final class SemaphoreBuilder {

    private SemaphoreBuilder() {
    }

    public static List<SemaphoreEntry> entries(Pensum pensum, List<SinuRecord> records) {
        Map<String, SinuRecord> latest = latestByCode(records);
        List<PensumCourse> items = pensum.coursesInDisplayOrder();

        Set<String> itemCodes = new HashSet<>();
        items.stream().map(PensumCourse::sinuCode).filter(code -> code != null).forEach(itemCodes::add);
        Deque<SinuRecord> unplaced = new ArrayDeque<>(latest.values().stream()
                .filter(r -> !itemCodes.contains(r.sinuCode()))
                .sorted(Comparator.comparing(SinuRecord::period, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SinuRecord::sinuCode))
                .toList());

        Map<String, SemaphoreEntry> byItem = new LinkedHashMap<>();
        for (PensumCourse item : items) {
            SinuRecord own = item.sinuCode() == null ? null : latest.get(item.sinuCode());
            if (own != null) {
                byItem.put(item.pensumItemCode(), entry(item, own, item.electiveSlot()));
            }
        }
        for (PensumCourse slot : items) {
            if (slot.electiveSlot() && !byItem.containsKey(slot.pensumItemCode()) && !unplaced.isEmpty()) {
                byItem.put(slot.pensumItemCode(), entry(slot, unplaced.poll(), true));
            }
        }

        List<SemaphoreEntry> entries = new ArrayList<>(items.size());
        for (PensumCourse item : items) {
            entries.add(byItem.getOrDefault(item.pensumItemCode(), SemaphoreEntry.pending(item.pensumItemCode())));
        }
        return entries;
    }

    private static SemaphoreEntry entry(PensumCourse item, SinuRecord record, boolean slot) {
        return new SemaphoreEntry(item.pensumItemCode(), SinuStatuses.of(record.status()), record.status(),
                record.period(), record.grade(),
                slot ? record.sinuCode() : null,
                slot ? record.name() : null);
    }

    /** The latest attempt at each course: the most recent period, then the furthest along. */
    private static Map<String, SinuRecord> latestByCode(List<SinuRecord> records) {
        Map<String, SinuRecord> latest = new LinkedHashMap<>();
        for (SinuRecord record : records) {
            if (record.sinuCode() == null) {
                continue;
            }
            latest.merge(record.sinuCode(), record, (a, b) -> LATER.compare(a, b) >= 0 ? a : b);
        }
        return latest;
    }

    private static final List<CourseStatus> FURTHEST_LAST = List.of(CourseStatus.PENDING, CourseStatus.FAILED,
            CourseStatus.POSTPONED, CourseStatus.IN_PROGRESS, CourseStatus.PASSED);

    private static final Comparator<SinuRecord> LATER = Comparator
            .comparing(SinuRecord::period, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(r -> FURTHEST_LAST.indexOf(SinuStatuses.of(r.status())));
}

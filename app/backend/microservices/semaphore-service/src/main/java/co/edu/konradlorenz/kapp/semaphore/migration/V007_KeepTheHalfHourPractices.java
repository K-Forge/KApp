package co.edu.konradlorenz.kapp.semaphore.migration;

import co.edu.konradlorenz.kapp.semaphore.domain.Pensum;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumArea;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumCourse;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Puts the half back on the four practices whose hours were rounded up.
 *
 * <p>Weekly hours used to be a whole number, so the four items the published plans print with a
 * half - Psicología's two professional practices at 4,5 hours, and the practices of Marketing
 * and Negocios Internacionales at 1,5 - were stored rounded. That is what made Psicología add up
 * to 175 weekly hours against the 174 its own document prints: a plan disagreeing with its own
 * printed total, for a reason that was ours and not the document's.
 *
 * <p>The seed files now carry the printed figure, so a fresh database is right from the start and
 * this change unit finds nothing to do there. It exists for the databases that already ran
 * {@code V006} - the shared Atlas cluster among them.
 *
 * <h2>Only where the stored figure is still the rounded one</h2>
 * Each item is addressed by its pensum and its item code, and touched only while it still holds
 * exactly the value the rounding produced. An item somebody has since corrected by hand is left
 * alone, and running this twice does nothing the second time.
 *
 * <h2>Totals follow only where they were derived</h2>
 * An area's hours are always a sum of its items - the plans print no other figure for an area -
 * so an area moves with the item. A pensum's {@code totalHours} is different: it is what the
 * printed plan declares, and only where the plan declares none did the seed add one up.
 * Marketing and Negocios Internacionales are those two, and they are named in
 * {@link #DERIVED_TOTALS}; Psicología's 174 is printed, so it stays exactly where it is. That is
 * the whole point of keeping declared totals: the 174 was right, and it was the items that were
 * wrong.
 *
 * <p>Which plan declares its own total is written down rather than inferred from whether the
 * figures happen to agree. Once this change unit has run they do agree, and a rule that reads
 * "the total equals its items, so it must have been derived" would then move Psicología's
 * printed 174 to 175 on the way back - reintroducing, as a rollback, the exact mistake this
 * removes.
 *
 * <p>Change units are append-only. Never edit one that has run; add a new one.
 */
@ChangeUnit(id = "semaphore-half-hour-practices-v007", order = "007", author = "kapp")
public class V007_KeepTheHalfHourPractices {

    private static final Logger log = LoggerFactory.getLogger(V007_KeepTheHalfHourPractices.class);

    /** pensumCode -> (pensumItemCode -> the weekly hours the plan prints). */
    static final Map<String, Map<String, Double>> PRINTED_HOURS = Map.of(
            "PSI-2020", Map.of("P5805", 4.5, "P5905", 4.5),
            "MKT-2026", Map.of("MKT-902", 1.5),
            "ANI-2026", Map.of("ANI-903", 1.5));

    /**
     * The plans whose brochure prints no total of weekly hours, so the seed added one up. Theirs
     * moves with the items; a total the document itself prints does not.
     */
    static final Set<String> DERIVED_TOTALS = Set.of("MKT-2026", "ANI-2026");

    @Execution
    public void restore(MongoTemplate mongo) {
        apply(mongo, true);
    }

    /**
     * Rounds the four back up, which is what a database that never ran this held. The
     * disagreement it puts back into Psicología is the one recorded in
     * {@code docs/pensums/PREGUNTAS-PENDIENTES.md}.
     */
    @RollbackExecution
    public void round(MongoTemplate mongo) {
        apply(mongo, false);
    }

    private void apply(MongoTemplate mongo, boolean toPrinted) {
        List<String> updated = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> plan : PRINTED_HOURS.entrySet()) {
            Pensum stored = mongo.findById(plan.getKey(), Pensum.class);
            if (stored == null) {
                continue;
            }
            Pensum rewritten = withHours(stored, plan.getValue(), toPrinted);
            if (rewritten != stored) {
                mongo.save(rewritten);
                updated.add(stored.pensumCode());
            }
        }
        log.info("Half-hour practices set to {} in {}",
                toPrinted ? "the printed figure" : "the rounded figure", updated);
    }

    /**
     * @param printed   the item codes to move, and the hours their plan prints
     * @param toPrinted true to move each from the rounded figure to the printed one, false for
     *                  the way back
     * @return the rewritten pensum, or the same instance - by identity - when no item still
     *         holds the value it is being moved from
     */
    static Pensum withHours(Pensum pensum, Map<String, Double> printed, boolean toPrinted) {
        Map<String, PensumCourse> items = pensum.byPensumItemCode();
        Map<String, Double> wanted = new HashMap<>();
        for (Map.Entry<String, Double> item : printed.entrySet()) {
            double rounded = Math.ceil(item.getValue());
            double from = toPrinted ? rounded : item.getValue();
            PensumCourse stored = items.get(item.getKey());
            if (stored != null && stored.weeklyHours() == from) {
                wanted.put(item.getKey(), toPrinted ? item.getValue() : rounded);
            }
        }
        if (wanted.isEmpty()) {
            return pensum;
        }

        Map<String, Double> areaDeltas = new HashMap<>();
        double totalDelta = 0;
        for (Map.Entry<String, Double> item : wanted.entrySet()) {
            PensumCourse stored = items.get(item.getKey());
            double delta = item.getValue() - stored.weeklyHours();
            areaDeltas.merge(stored.area(), delta, Double::sum);
            totalDelta += delta;
        }

        List<PensumCourse> courses = pensum.courses().stream()
                .map(c -> wanted.containsKey(c.pensumItemCode())
                        ? new PensumCourse(c.code(), c.pensumItemCode(), c.name(), c.level(),
                                c.credits(), wanted.get(c.pensumItemCode()), c.area(),
                                c.electiveSlot(), c.prerequisites(), c.sinuCode())
                        : c)
                .toList();

        List<PensumArea> areas = pensum.areas().stream()
                .map(a -> areaDeltas.containsKey(a.code())
                        ? new PensumArea(a.code(), a.name(), a.color(), a.credits(),
                                a.hours() + areaDeltas.get(a.code()))
                        : a)
                .toList();

        double totalHours = DERIVED_TOTALS.contains(pensum.pensumCode())
                ? pensum.totalHours() + totalDelta
                : pensum.totalHours();

        return new Pensum(pensum.pensumCode(), pensum.programCode(), pensum.programName(),
                pensum.faculty(), pensum.reform(), pensum.status(), pensum.totalCredits(),
                totalHours, pensum.levels(), areas, courses);
    }
}

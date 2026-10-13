package co.edu.konradlorenz.kapp.schedule.color;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The colour each course of a timetable is drawn in, so a week reads at a glance.
 *
 * <p>From the six colours of {@code docs/K-COLORS.md} other than its pink. Each course starts from a
 * colour of its own, worked out from its code, so it keeps it from one period to the next. Two
 * courses of one timetable never share one while there are colours left: the second moves on to the
 * next free colour, in the order of their codes, so the same timetable is always painted the same way.
 * Nothing is stored.
 */
public final class CourseColors {

    static final List<String> PALETTE = List.of("#539392", "#C9D329", "#522567", "#3E823E", "#B62325", "#592E2A");

    private CourseColors() {
    }

    /** @return the colour of each of the codes */
    public static Map<String, String> assign(Collection<String> sinuCodes) {
        Map<String, String> colors = new HashMap<>();
        Set<Integer> taken = new HashSet<>();
        for (String code : sinuCodes.stream().distinct().sorted().toList()) {
            int own = Math.floorMod(code.hashCode(), PALETTE.size());
            int chosen = own;
            for (int step = 0; step < PALETTE.size() && taken.contains(chosen); step++) {
                chosen = (own + step + 1) % PALETTE.size();
            }
            if (taken.size() >= PALETTE.size()) {
                chosen = own;
            }
            taken.add(chosen);
            colors.put(code, PALETTE.get(chosen));
        }
        return colors;
    }
}

package co.edu.konradlorenz.kapp.semaphore.sinu;

import java.util.List;

/**
 * A course SINU offers in one period to fill elective slots.
 *
 * @param slots the pensumItemCodes of the slots it fills; empty means any elective slot
 */
public record SinuElectiveOffering(String sinuCode, String name, int credits, double weeklyHours,
                                   List<String> slots) {

    public SinuElectiveOffering {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    /** @return whether this course may fill that elective slot */
    public boolean fills(String slotPensumItemCode) {
        return slots.isEmpty() || slots.contains(slotPensumItemCode);
    }
}

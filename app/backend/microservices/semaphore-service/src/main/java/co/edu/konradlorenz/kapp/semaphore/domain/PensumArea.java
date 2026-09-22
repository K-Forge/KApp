package co.edu.konradlorenz.kapp.semaphore.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * A knowledge area - one row of the semaforo grid.
 *
 * <p>Area codes are defined per pensum rather than globally: a psychology pensum has
 * nothing to gain from the engineering areas. The Ingenieria de Sistemas Reforma 2018 plan
 * uses {@code CB}, {@code BIS}, {@code ISA} and {@code SI}.
 *
 * <p>{@code credits} and {@code hours} are the totals the <em>printed plan</em> declares
 * for the area, not sums recomputed from {@link PensumCourse}. They are kept as
 * declared on purpose: when the two disagree, the discrepancy is reported rather than
 * papered over. See {@code NEEDS_VERIFICATION.md}.
 *
 * @param code    short area code, unique within the pensum
 * @param name    display name
 * @param color   brand colour as an RGB hex triplet, used to tint the row
 * @param credits credits the plan assigns to this area, as declared
 * @param hours   <strong>weekly</strong> hours the plan assigns to this area, as declared;
 *                whole or half, because the items it adds up can be
 */
public record PensumArea(
        String code,
        String name,
        String color,
        int credits,
        @JsonSerialize(using = WeeklyHours.Serializer.class) double hours
) {
}

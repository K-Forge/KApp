package co.edu.konradlorenz.kapp.map.domain;

import java.time.Instant;

/**
 * One distance taken on site round a campus's blocks, with a tape or a phone's Measure app: how
 * far a wall stands from the curb, or how long it is.
 *
 * @param id        which distance of the survey plan it is, or an extra one's own id
 * @param label     what an extra distance is of; blank for one the plan names
 * @param text      what was typed, as it was typed: "4,80 + 3,25" when it was taken in pieces
 * @param metres    the total the portal read from it; null while it reads none
 * @param note      what was seen on site that the sketch does not show
 * @param updatedAt when it was last changed
 */
public record SurveyMeasure(String id, String label, String text, Double metres, String note, Instant updatedAt) {
}

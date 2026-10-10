package co.edu.konradlorenz.kapp.semaphore.domain;

/**
 * One item of a plan pinned to a level other than the one its pensum gives it, or an elective
 * slot with the course the student means to take in it.
 *
 * @param pensumItemCode   the item, by the identifier every per-item endpoint takes, fixed course
 *                         or elective slot alike
 * @param plannedLevel     the level the student intends to take it in
 * @param electiveSinuCode for an elective slot, the course from the elective bank the student
 *                         means to take; null for a fixed course or a slot with none chosen. A
 *                         plan, not an enrolment: the bank is checked when the plan is read
 */
public record Placement(String pensumItemCode, int plannedLevel, String electiveSinuCode) {
}

package co.edu.konradlorenz.kapp.semaphore.sinu;

/**
 * One course of a student's record, exactly as SINU gives it.
 *
 * @param status as SINU writes it - {@code APROBADA}, {@code EN CURSO}, {@code PERDIDA},
 *               {@code APLAZADA} - mapped to the semáforo's own by {@code service.SinuStatuses}
 * @param period when it was taken, {@code YYYYS}
 * @param grade  on the university's {@code 0..50} scale; null when SINU does not give one
 */
public record SinuRecord(String sinuCode, String name, String status, String period, Integer grade) {
}

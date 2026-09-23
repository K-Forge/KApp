package co.edu.konradlorenz.kapp.map.domain;

/**
 * One arm of a building, as that building names it.
 *
 * <p>This used to be an enum of NORTE, SUR and CENTRAL - the central building's vocabulary, baked
 * into the code. The survey found the first building that does not use it: Bienestar has a west
 * wing of welfare services and an east wing of classrooms and labs. So wings are declared per
 * building, the way pensum areas are declared per pensum, and a space refers to one by code.
 *
 * @param code       short identifier within the building: {@code N}, {@code OCC}
 * @param name       what people call it: "Ala norte", "Ala occidental"
 * @param doorSuffix what the doors in this wing append to the room number, or null when they
 *                   append nothing. The central building's south wing prints {@code 503-S} on the
 *                   door, so its suffix is {@code -S}; Bienestar's rooms are plain {@code 301} in
 *                   either wing. Declared rather than guessed from the last characters of a code,
 *                   which is what the old enum did and what broke on {@code ESC-SUR}
 * @param note       how to get into or across this wing, where that is not obvious - "se cruza
 *                   por el P1 o por la terraza"
 */
public record Wing(
        String code,
        String name,
        String doorSuffix,
        String note
) {
}

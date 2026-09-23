package co.edu.konradlorenz.kapp.map.domain;

/**
 * The fixed family a space type belongs to. The clients choose a colour and an icon by category,
 * which is why this is closed while the types under it are not.
 *
 * <p>Types are data: an administrator adds "Sala de lactancia" from the portal the moment one
 * turns up on a floor, and nothing has to be released for it. That only works because no client
 * ever needs to know a type in advance - it knows the seven categories, and a type it has never
 * seen still lands in one of them. Adding a category is therefore a contract change, made on
 * purpose, not something a form can do.
 *
 * <p>Six of them were agreed from the survey. {@link #OTHER} is the seventh, and exists because
 * the survey itself produces spaces nobody can classify yet: the evacuation plans draw rooms with
 * no name at all, and those have to be stored as something without pretending to be an office.
 */
public enum SpaceCategory {
    /** Docencia: aulas, laboratorios, salas de cómputo, auditorios, biblioteca. */
    TEACHING,
    /** Atención: ventanillas, recepción, consultorios, enfermería, fotocopiadora. */
    PUBLIC_SERVICE,
    /** Oficina: direcciones, decanaturas, salas de juntas, archivo. */
    OFFICE,
    /** Bienestar y social: cafeterías, gimnasio, salas de juego, terrazas. */
    SOCIAL,
    /** Servicio: baños, cuartos de aseo y de TI, planta eléctrica, parqueadero. */
    FACILITIES,
    /** Circulación: ascensores, escaleras, rampas, entradas. What {@code accessVia} may point at. */
    CIRCULATION,
    /** Sin identificar: drawn on a plan, not yet known. */
    OTHER
}

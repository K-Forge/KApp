package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.Corridor;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.FloorStatus;
import co.edu.konradlorenz.kapp.map.domain.GridPoint;
import co.edu.konradlorenz.kapp.map.domain.SpaceCategory;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.domain.SpaceTypeDocument;
import co.edu.konradlorenz.kapp.map.domain.Wing;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.TextIndexDefinition.TextIndexDefinitionBuilder;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Moves the map to the model the campus survey called for, and seeds the placeholder campus in
 * it.
 *
 * <p>What changed, and why, is in {@code docs/map/LEVANTAMIENTO.md}: floors are identified by a
 * code because a mezzanine has no integer level; wings are declared per building because
 * Bienestar's are west and east; spaces have a door code apart from their identifier because most
 * dependencies have nothing on their door; types are a catalogue because the survey keeps finding
 * kinds of room nobody listed; and accessibility is recorded, starting from "unknown".
 *
 * <h2>What it does, in order</h2>
 * <ol>
 *   <li>Seeds the space type catalogue, keeping every code the old enum had so existing spaces
 *       keep their type.</li>
 *   <li>Removes the old placeholder campus ({@code placeholder = true}).</li>
 *   <li>Converts whatever else is stored - buildings and spaces somebody entered through the
 *       portal - from the old shape to the new one. Written against raw documents rather than
 *       the domain records, which no longer describe the old shape.</li>
 *   <li>Rebuilds the text index over the door code instead of the internal code, and adds the
 *       index a floor read uses.</li>
 *   <li>Seeds the new placeholder campus, which exercises every part of the new model.</li>
 * </ol>
 *
 * <p>Re-running it is harmless: the catalogue is only added to, converted documents are skipped
 * because they already have the new fields, and the placeholder campus is removed before it is
 * written.
 */
@ChangeUnit(id = "map-model-v2-v004", order = "004", author = "kapp")
public class V004_MapModelV2 {

    private static final Logger log = LoggerFactory.getLogger(V004_MapModelV2.class);

    private static final String CAMPUS = "Sede Principal";
    private static final String NORTE_COLOR = "#F2A93B";
    private static final String SUR_COLOR = "#4C9F70";
    private static final String CENTRAL_COLOR = "#5B8DEF";

    /** The old wing enum, and the wing each value becomes on its building. */
    private static final Map<String, Wing> LEGACY_WINGS = Map.of(
            "NORTE", new Wing("N", "Ala norte", "-N", null),
            "SUR", new Wing("S", "Ala sur", "-S", null),
            "CENTRAL", new Wing("C", "Ala central", "-C", null));

    /** Types whose old codes were invented identifiers, not something printed on a door. */
    private static final Set<String> LEGACY_UNNUMBERED = Set.of("ELEVATOR", "STAIRS", "ENTRANCE", "CORRIDOR");

    /**
     * The first catalogue. Every value of the old enum is here under its own code, and the rest
     * is what the survey of the real campus found: see the table in {@code docs/map/LEVANTAMIENTO.md}.
     */
    static final List<SpaceTypeDocument> CATALOGUE = catalogue(
            SpaceCategory.TEACHING, "CLASSROOM", "Aula", "LAB", "Laboratorio",
            "COMPUTER_LAB", "Sala de cómputo", "AUDITORIUM", "Auditorio", "LIBRARY", "Biblioteca",
            "GESELL_CHAMBER", "Cámara de Gesell",
            SpaceCategory.PUBLIC_SERVICE, "ADMIN_OFFICE", "Ventanilla de atención", "RECEPTION", "Recepción",
            "WAITING_ROOM", "Sala de espera", "CONSULTATION_ROOM", "Consultorio",
            "MEDICAL_OFFICE", "Consultorio médico", "NURSING", "Enfermería", "PRINT_SHOP", "Fotocopiadora",
            SpaceCategory.OFFICE, "OFFICE", "Oficina", "MEETING_ROOM", "Sala de juntas",
            "EVENT_HALL", "Salón de eventos", "ARCHIVE", "Archivo",
            SpaceCategory.SOCIAL, "CAFETERIA", "Cafetería", "SOCIAL_AREA", "Zona social", "GYM", "Gimnasio",
            "GAME_ROOM", "Sala de juegos", "ARTS_ROOM", "Sala de música o danza", "TERRACE", "Terraza",
            "WELLBEING", "Bienestar",
            SpaceCategory.FACILITIES, "RESTROOM", "Baño", "CLEANING_ROOM", "Cuarto de aseo", "IT_ROOM", "Cuarto de TI",
            "ELECTRICAL_ROOM", "Planta eléctrica", "WASTE_ROOM", "Centro de acopio",
            "LOCKER_ROOM", "Vestier o lockers", "STORAGE", "Bodega", "PARKING", "Parqueadero",
            SpaceCategory.CIRCULATION, "ELEVATOR", "Ascensor", "STAIRS", "Escalera", "RAMP", "Rampa",
            "CORRIDOR", "Pasillo", "ENTRANCE", "Entrada",
            SpaceCategory.OTHER, "OTHER", "Sin identificar");

    @Execution
    public void execute(MongoTemplate mongo) {
        int typesAdded = seedCatalogue(mongo);

        mongo.remove(Query.query(Criteria.where("placeholder").is(true)), "spaces");
        mongo.remove(Query.query(Criteria.where("placeholder").is(true)), "buildings");

        int converted = convertLegacy(mongo);
        rebuildIndexes(mongo);
        seedPlaceholderCampus(mongo);

        log.info("Map model v2: {} space type(s) added, {} building(s) converted, placeholder campus seeded",
                typesAdded, converted);
    }

    /**
     * Removes the placeholder campus this change unit wrote. Converted documents stay converted:
     * the old shape is not something the current code can read, so putting it back would break
     * the service rather than restore it.
     */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        mongo.remove(Query.query(Criteria.where("placeholder").is(true)), "spaces");
        mongo.remove(Query.query(Criteria.where("placeholder").is(true)), "buildings");
    }

    private static int seedCatalogue(MongoTemplate mongo) {
        int added = 0;
        for (SpaceTypeDocument type : CATALOGUE) {
            if (mongo.findById(type.code(), SpaceTypeDocument.class) == null) {
                mongo.insert(type);
                added++;
            }
        }
        return added;
    }

    /**
     * Converts buildings and spaces still in the old shape, recognised by what they lack: a
     * floor without a {@code code}, a space without a {@code typeCode}.
     *
     * @return how many buildings were converted
     */
    static int convertLegacy(MongoTemplate mongo) {
        int converted = 0;
        for (Document building : mongo.getCollection("buildings").find()) {
            @SuppressWarnings("unchecked")
            List<Document> floors = (List<Document>) building.getOrDefault("floors", List.of());
            boolean legacy = floors.stream().anyMatch(f -> !f.containsKey("code"));
            if (!legacy) {
                continue;
            }
            String buildingId = building.getString("_id");
            List<Document> spaces = mongo.getCollection("spaces")
                    .find(new Document("buildingId", buildingId)).into(new ArrayList<>());

            Set<String> usedWings = new LinkedHashSet<>();
            spaces.forEach(space -> {
                Object wing = space.get("wing");
                if (wing instanceof String name && LEGACY_WINGS.containsKey(name)) {
                    usedWings.add(name);
                }
            });

            List<Document> newFloors = new ArrayList<>();
            for (Document floor : floors) {
                int level = ((Number) floor.get("level")).intValue();
                Document next = new Document(floor);
                next.put("code", floorCode(level));
                next.put("level", (double) level);
                next.put("status", FloorStatus.DRAFT.name());
                next.put("accessibility", Accessibility.UNKNOWN.name());
                next.put("version", 0L);
                newFloors.add(next);
            }
            List<Document> wings = usedWings.stream()
                    .map(LEGACY_WINGS::get)
                    .map(w -> new Document("code", w.code()).append("name", w.name())
                            .append("doorSuffix", w.doorSuffix()))
                    .toList();

            mongo.getCollection("buildings").updateOne(new Document("_id", buildingId),
                    new Document("$set", new Document("floors", newFloors)
                            .append("wings", wings)
                            .append("aliases", building.getOrDefault("aliases", List.of()))));

            for (Document space : spaces) {
                if (space.containsKey("typeCode")) {
                    continue;
                }
                String type = space.getString("type");
                int level = ((Number) space.get("floorLevel")).intValue();
                Object wing = space.get("wing");
                String code = space.getString("code");
                boolean unnumbered = LEGACY_UNNUMBERED.contains(type);

                Document set = new Document("typeCode", type)
                        .append("floorCode", floorCode(level))
                        .append("floorLevel", (double) level)
                        .append("wing", wing instanceof String name && LEGACY_WINGS.containsKey(name)
                                ? LEGACY_WINGS.get(name).code() : null)
                        .append("doorCode", unnumbered ? null : code)
                        .append("baseCode", unnumbered ? null : space.get("baseCode"));
                mongo.getCollection("spaces").updateOne(new Document("_id", space.get("_id")),
                        new Document("$set", set).append("$unset", new Document("type", "")));
            }
            converted++;
        }
        return converted;
    }

    /**
     * The old integer level, as the code a floor now goes by: {@code S1} for the first basement,
     * {@code P0} for a ground level numbered zero, {@code P3} for the third floor.
     */
    static String floorCode(int level) {
        if (level < 0) {
            return "S" + (-level);
        }
        return "P" + level;
    }

    private static void rebuildIndexes(MongoTemplate mongo) {
        for (IndexInfo index : mongo.indexOps("spaces").getIndexInfo()) {
            if ("tx_spaces_search".equals(index.getName())) {
                mongo.indexOps("spaces").dropIndex("tx_spaces_search");
            }
        }
        // The door code, not the internal code: a generated identifier must never be what a
        // search matches. Weights as before - an exact door number above a name, a name above an
        // alias.
        mongo.indexOps("spaces").createIndex(
                new TextIndexDefinitionBuilder()
                        .named("tx_spaces_search")
                        .withDefaultLanguage("spanish")
                        .onField("doorCode", 5F)
                        .onField("name", 3F)
                        .onField("aliases", 2F)
                        .build());

        // Every floor read asks for one building's one floor.
        mongo.indexOps("spaces").createIndex(
                new Index().on("buildingId", Sort.Direction.ASC)
                        .on("floorCode", Sort.Direction.ASC)
                        .named("ix_spaces_building_floor"));
    }

    // ── The placeholder campus ────────────────────────────────────────────────────────────

    /**
     * *** PLACEHOLDER DATA - NOT THE REAL CAMPUS ***
     *
     * <p>Two invented buildings that exercise every part of the model, so the clients can be
     * built against something that behaves like the real campus until the survey replaces it:
     * three wings with door suffixes and a basement in Bloque A; a mezzanine reachable only by
     * stairs, a dependency with nothing on its door and a space not yet placed in Bloque B.
     *
     * <p>Room {@code 302} keeps its three deliberately mismatched aliases against the door name
     * "Laboratorio de Sistemas": alias search is the feature most likely to look like it works
     * while being subtly broken, and the search tests lean on it.
     */
    private static void seedPlaceholderCampus(MongoTemplate mongo) {
        Instant now = Instant.now();

        List<Wing> wingsA = List.of(
                new Wing("C", "Ala central", null, null),
                new Wing("N", "Ala norte", "-N", null),
                new Wing("S", "Ala sur", "-S", null));
        BuildingDocument bloqueA = mongo.save(new BuildingDocument(
                UUID.randomUUID().toString(), "A", "Bloque A", CAMPUS,
                "Main academic building. Classrooms, computer labs and faculty offices.",
                List.of("Edificio principal"),
                wingsA,
                List.of(basement(), floor("P1", 1, "Piso 1"), floor("P2", 2, "Piso 2"),
                        floor("P3", 3, "Piso 3"), floor("P6", 6, "Piso 6"), floor("P7", 7, "Piso 7")),
                true, now, now));

        BuildingDocument bloqueB = mongo.save(new BuildingDocument(
                UUID.randomUUID().toString(), "B", "Bloque B", CAMPUS,
                "Psychology building. Research labs, library and student wellbeing services.",
                List.of("Bloque de psicología"),
                List.of(),
                List.of(floor("P1", 1, "Piso 1"),
                        new Floor("MEZZ", 1.5, "Mezzanine", FloorStatus.DRAFT, Accessibility.STAIRS_ONLY,
                                "Solo por la escalera, desde la recepción.", 6, 10, List.of(), 0),
                        floor("P2", 2, "Piso 2"),
                        new Floor("P3", 3, "Piso 3", FloorStatus.UNMAPPED, Accessibility.UNKNOWN,
                                null, 11, 16, List.of(), 0)),
                true, now, now));

        // ── Circulation first: everything else points at it through accessVia ─────────────
        space(mongo, bloqueA, "ASC-CENTRAL", null, "C", "Ascensor central", "ELEVATOR", "P1",
                List.of("ascensor", "elevador"), 5, 7, 1, 1, null, null, now);
        space(mongo, bloqueA, "ESC-NORTE", null, "N", "Escaleras norte", "STAIRS", "P1",
                List.of("escalera norte"), 1, 2, 1, 1, null, null, now);
        space(mongo, bloqueA, "ESC-SUR", null, "S", "Escaleras sur", "STAIRS", "P1",
                List.of("escalera sur"), 9, 2, 1, 1, null, null, now);
        space(mongo, bloqueA, "ENT-PRINCIPAL", null, "C", "Entrada principal", "ENTRANCE", "P1",
                List.of("entrada", "porteria"), 5, 0, 1, 1, null, null, now);

        // ── The three wings: same number, three different rooms on one floor ──────────────
        space(mongo, bloqueA, "301", "301", "C", "Aula 301", "CLASSROOM", "P3",
                List.of("salon 301"), 5, 4, 1, 2, "ASC-CENTRAL", 30, now);
        space(mongo, bloqueA, "301-N", "301-N", "N", "Aula 301 Norte", "CLASSROOM", "P3",
                List.of("salon 301 norte"), 1, 4, 1, 2, "ESC-NORTE", 28, now);
        space(mongo, bloqueA, "301-S", "301-S", "S", "Aula 301 Sur", "CLASSROOM", "P3",
                List.of("salon 301 sur"), 9, 4, 1, 2, "ESC-SUR", 28, now);

        // The alias-hard fixture: the door says "Laboratorio de Sistemas", students type any of
        // three other names.
        space(mongo, bloqueA, "302", "302", "C", "Laboratorio de Sistemas", "LAB", "P3",
                List.of("sala de sistemas", "lab 302", "laboratorio 302"),
                5, 7, 2, 3, "ASC-CENTRAL", 25, now);

        space(mongo, bloqueA, "612", "612", "C", "Aula 612", "CLASSROOM", "P6",
                List.of("sala 612", "salon 612"), 3, 6, 1, 2, "ASC-CENTRAL", 35, now);
        space(mongo, bloqueA, "708", "708", "C", "Aula 708", "CLASSROOM", "P7",
                List.of("sala 708", "salon 708"), 3, 4, 1, 2, "ASC-CENTRAL", 30, now);
        space(mongo, bloqueA, "709", "709", "C", "Aula 709", "CLASSROOM", "P7",
                List.of("sala 709", "salon 709"), 3, 7, 1, 2, "ASC-CENTRAL", 30, now);
        space(mongo, bloqueA, "710", "710", "C", "Aula 710", "CLASSROOM", "P7",
                List.of("sala 710", "salon 710"), 7, 4, 1, 2, "ASC-CENTRAL", 28, now);
        space(mongo, bloqueA, "711", "711", "C", "Aula 711", "CLASSROOM", "P7",
                List.of("sala 711", "salon 711"), 7, 7, 1, 2, "ASC-CENTRAL", 28, now);

        // A room that spans several cells, so the client honours rowSpan and colSpan.
        space(mongo, bloqueA, "310", "310", "C", "Auditorio Konrad Lorenz", "AUDITORIUM", "P3",
                List.of("auditorio"), 7, 11, 3, 4, "ASC-CENTRAL", 180, now);

        // ── The basement, where the first-digit rule does not apply ────────────────────────
        space(mongo, bloqueA, "S-01", "S-01", "C", "Parqueadero", "PARKING", "S1",
                List.of("parqueadero", "sotano"), 2, 2, 4, 6, "ASC-CENTRAL", null, now);
        space(mongo, bloqueA, "S-02", "S-02", "C", "Depósito", "STORAGE", "S1",
                List.of("deposito", "bodega"), 7, 2, 1, 2, "ASC-CENTRAL", null, now);

        // ── Bloque B ──────────────────────────────────────────────────────────────────────
        space(mongo, bloqueB, "B-ASC", null, null, "Ascensor Bloque B", "ELEVATOR", "P1",
                List.of("ascensor bloque b"), 4, 5, 1, 1, null, null, now);
        space(mongo, bloqueB, "B-ESC", null, null, "Escalera Bloque B", "STAIRS", "P1",
                List.of("escalera bloque b"), 4, 7, 1, 1, null, null, now);
        space(mongo, bloqueB, "101", "101", null, "Biblioteca", "LIBRARY", "P1",
                List.of("sala de lectura"), 2, 2, 2, 3, "B-ASC", null, now);
        // Stored WITH its accent on purpose: the unaccented "cafeteria" still has to find it.
        space(mongo, bloqueB, "102", "102", null, "Cafetería", "CAFETERIA", "P1",
                List.of("cafeteria bloque b"), 6, 2, 2, 3, "B-ASC", null, now);
        // A dependency with nothing on its door, on a floor only stairs reach.
        space(mongo, bloqueB, "B-MEZZ-01", null, null, "Laboratorio de Interactividad", "LAB", "MEZZ",
                List.of("interactividad"), 2, 2, 2, 4, "B-ESC", null, now);
        space(mongo, bloqueB, "205", "205", null, "Bienestar Universitario", "WELLBEING", "P2",
                List.of("bienestar", "psicologia"), 4, 3, 1, 2, "B-ASC", null, now);
        space(mongo, bloqueB, "210", "210", null, "Terraza", "TERRACE", "P2",
                List.of("terraza"), 1, 7, 2, 3, "B-ASC", null, now);
        // Known to be on the floor from its plaque, not yet placed on the grid.
        space(mongo, bloqueB, "B-P3-01", null, null, "Sala de juntas", "MEETING_ROOM", "P3",
                List.of(), null, null, 1, 1, "B-ASC", null, now);
    }

    private static Floor basement() {
        return new Floor("S1", -1, "Sótano", FloorStatus.DRAFT, Accessibility.STEP_FREE, null, 10, 10,
                List.of(new Corridor("PAS-S-CENTRAL", "Pasillo sótano", CENTRAL_COLOR,
                        List.of(new GridPoint(6, 0), new GridPoint(6, 9)))), 0);
    }

    private static Floor floor(String code, double level, String name) {
        return new Floor(code, level, name, FloorStatus.DRAFT, Accessibility.STEP_FREE, null, 11, 16, List.of(
                new Corridor("PAS-CENTRAL", "Pasillo central", CENTRAL_COLOR,
                        List.of(new GridPoint(5, 0), new GridPoint(5, 15))),
                new Corridor("PAS-NORTE", "Pasillo norte", NORTE_COLOR,
                        List.of(new GridPoint(5, 3), new GridPoint(1, 3), new GridPoint(1, 15))),
                new Corridor("PAS-SUR", "Pasillo sur", SUR_COLOR,
                        List.of(new GridPoint(5, 3), new GridPoint(9, 3), new GridPoint(9, 15)))), 0);
    }

    private static void space(MongoTemplate mongo, BuildingDocument building, String code, String doorCode,
                              String wing, String name, String typeCode, String floorCode,
                              List<String> aliases, Integer gridRow, Integer gridColumn,
                              int rowSpan, int colSpan, String accessVia, Integer capacity, Instant now) {
        Floor floor = building.floor(floorCode).orElseThrow();
        mongo.save(new SpaceDocument(
                UUID.randomUUID().toString(), code, doorCode,
                SpaceDocument.baseCodeOf(doorCode, building.wings()),
                wing, name, typeCode, building.id(), building.code(), building.campus(),
                floorCode, floor.level(), aliases, gridRow, gridColumn, rowSpan, colSpan,
                accessVia, null, null, capacity, true, now, now));
    }

    private static List<SpaceTypeDocument> catalogue(Object... entries) {
        Instant now = Instant.now();
        List<SpaceTypeDocument> types = new ArrayList<>();
        SpaceCategory category = null;
        for (int i = 0; i < entries.length; i++) {
            if (entries[i] instanceof SpaceCategory next) {
                category = next;
            } else {
                String code = (String) entries[i];
                String name = (String) entries[++i];
                types.add(new SpaceTypeDocument(code, name, category, now, now));
            }
        }
        return List.copyOf(types);
    }
}

package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.FloorStatus;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.service.MapMapper;
import co.edu.konradlorenz.kapp.map.web.dto.LayoutSpaceDto;
import co.edu.konradlorenz.kapp.map.web.dto.PointDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A floor named in the portal, redrawn by the snapshot: the names go onto the new drawing.
 *
 * <p>The base has two boxes and an inventoried cafeteria. In the portal somebody gave the first box
 * to the cafeteria - the editor moves the box's outline onto it and drops the box - and typed a
 * name into the second. The snapshot then moved both boxes.
 */
class FloorMergeTest {

    private static final Instant THEN = Instant.parse("2026-09-27T10:00:00Z");

    private final SurveySnapshot.SnapshotFloor base = floor(
            box("P1-01", rect(0, 0, 100, 100)),
            box("P1-02", rect(100, 0, 200, 100)),
            space("P1-CAFETERIA", "Cafetería No. 1", "CAFETERIA", null));

    private final SurveySnapshot.SnapshotFloor redrawn = floor(
            box("P1-01", rect(10, 0, 110, 100)),
            box("P1-02", rect(110, 0, 210, 100)),
            space("P1-CAFETERIA", "Cafetería No. 1", "CAFETERIA", null));

    @Test
    @DisplayName("a box given to a space gives it its new outline, and a name typed into a box stays on its new outline")
    void namesGoOntoTheNewDrawing() {
        List<SpaceDocument> stored = List.of(
                document(space("P1-CAFETERIA", "Cafetería No. 1", "CAFETERIA", rect(0, 0, 100, 100))),
                document(space("P1-02", "Tesorería", "OFFICE", rect(100, 0, 200, 100))));

        FloorMerge.Result merged = FloorMerge.merge(base, savedFloor(3), stored, redrawn);

        Map<String, LayoutSpaceDto> byCode = merged.spaces().stream()
                .collect(Collectors.toMap(LayoutSpaceDto::code, Function.identity()));
        assertThat(byCode).doesNotContainKey("P1-01");
        assertThat(byCode.get("P1-CAFETERIA").shape()).isEqualTo(rect(10, 0, 110, 100));
        assertThat(byCode.get("P1-02").name()).isEqualTo("Tesorería");
        assertThat(byCode.get("P1-02").typeCode()).isEqualTo("OFFICE");
        assertThat(byCode.get("P1-02").shape()).isEqualTo(rect(110, 0, 210, 100));
        assertThat(merged.floor().version()).as("still somebody's work").isEqualTo(4);
    }

    @Test
    @DisplayName("a room somebody redrew keeps their outline, and when it no longer fits the new drawing nothing is merged")
    void aRedrawnRoomThatNoLongerFitsKeepsTheFloor() {
        // Widened into where the snapshot now puts the other box, which nobody touched.
        List<SpaceDocument> stored = List.of(
                document(box("P1-01", rect(0, 0, 150, 100))),
                document(box("P1-02", rect(150, 0, 200, 100))),
                document(space("P1-CAFETERIA", "Cafetería No. 1", "CAFETERIA", null)));
        List<SpaceDocument> untouchedNeighbour = List.of(stored.getFirst(),
                document(box("P1-02", rect(100, 0, 200, 100))), stored.get(2));

        assertThat(FloorMerge.merge(base, savedFloor(2), untouchedNeighbour, redrawn))
                .as("the widened room would share floor with the neighbour's new outline").isNull();
        assertThat(FloorMerge.merge(base, savedFloor(2), stored, redrawn).spaces())
                .as("both redrawn in the portal: both outlines are theirs")
                .anyMatch(s -> s.code().equals("P1-01") && s.shape().equals(rect(0, 0, 150, 100)));
    }

    @Test
    @DisplayName("a floor nobody changed since the base is simply the new drawing")
    void nothingChangedIsTheNewDrawing() {
        List<SpaceDocument> stored = base.spaces().stream().map(FloorMergeTest::document).toList();

        FloorMerge.Result merged = FloorMerge.merge(base, savedFloor(1), stored, redrawn);

        assertThat(merged.spaces()).containsExactlyElementsOf(redrawn.spaces());
        assertThat(merged.carried()).isEmpty();
    }

    private static Floor savedFloor(long version) {
        return new Floor("P1", 1, "Piso 1", FloorStatus.DRAFT, Accessibility.UNKNOWN, null, 400, 300,
                List.of(), List.of(), version);
    }

    private static SurveySnapshot.SnapshotFloor floor(LayoutSpaceDto... spaces) {
        return new SurveySnapshot.SnapshotFloor("P1", 1, "Piso 1", FloorStatus.DRAFT, Accessibility.UNKNOWN, null,
                400, 300, null, List.of(), List.of(), List.of(spaces));
    }

    private static LayoutSpaceDto box(String code, List<PointDto> shape) {
        return space(code, "Sin identificar", "OTHER", shape);
    }

    private static LayoutSpaceDto space(String code, String name, String type, List<PointDto> shape) {
        return new LayoutSpaceDto(code, null, null, name, type, List.of(), shape, List.of(), null, null, null, null);
    }

    private static SpaceDocument document(LayoutSpaceDto space) {
        return new SpaceDocument(space.code(), space.code(), space.doorCode(), null, space.wing(), space.name(),
                space.typeCode(), "b", "EC", "Sede Principal", "P1", 1, space.aliasesOrEmpty(),
                MapMapper.toPoints(space.shape()), MapMapper.toDoors(space.doorsOrEmpty()), null, null, null, null,
                false, THEN, THEN);
    }

    private static List<PointDto> rect(int x0, int y0, int x1, int y1) {
        return List.of(new PointDto(x0, y0), new PointDto(x1, y0), new PointDto(x1, y1), new PointDto(x0, y1));
    }
}

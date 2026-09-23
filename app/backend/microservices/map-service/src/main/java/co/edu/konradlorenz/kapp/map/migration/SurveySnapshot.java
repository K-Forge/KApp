package co.edu.konradlorenz.kapp.map.migration;

import co.edu.konradlorenz.kapp.map.domain.Accessibility;
import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.Floor;
import co.edu.konradlorenz.kapp.map.domain.FloorStatus;
import co.edu.konradlorenz.kapp.map.service.MapMapper;
import co.edu.konradlorenz.kapp.map.web.dto.CorridorDto;
import co.edu.konradlorenz.kapp.map.web.dto.LayoutSpaceDto;
import co.edu.konradlorenz.kapp.map.web.dto.WingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The campus as the snapshot under {@code db/seed/map/} describes it: one file per building, in
 * the shape of the API - a building request whose floors carry their spaces.
 *
 * <p>The first snapshot was transcribed from the campus survey ({@code docs/map/LEVANTAMIENTO.md}):
 * every floor not drawn yet and every space in the inventory, waiting to be placed. From then on
 * {@code scripts/export-map-snapshot.sh} rewrites it from what has been drawn in the portal, so a
 * fresh database starts from the campus as it was last exported rather than from photos.
 */
public final class SurveySnapshot {

    static final String PATTERN = "classpath*:db/seed/map/*.json";

    private static final ObjectMapper JSON = new ObjectMapper();

    private SurveySnapshot() {
    }

    public record Building(
            String code,
            String name,
            String campus,
            String description,
            List<String> aliases,
            List<WingDto> wings,
            List<SnapshotFloor> floors
    ) {
        public Building {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            wings = wings == null ? List.of() : List.copyOf(wings);
            floors = floors == null ? List.of() : List.copyOf(floors);
        }

        /** The building as it is stored: real data, never a placeholder, every floor at version 0. */
        public BuildingDocument toDocument(Instant now) {
            return new BuildingDocument(UUID.randomUUID().toString(), code, name, campus, description,
                    aliases, wings.stream().map(MapMapper::toWing).toList(),
                    floors.stream().map(SnapshotFloor::toFloor).toList(), false, now, now);
        }
    }

    public record SnapshotFloor(
            String code,
            double level,
            String name,
            FloorStatus status,
            Accessibility accessibility,
            String note,
            int gridRows,
            int gridColumns,
            List<CorridorDto> corridors,
            List<LayoutSpaceDto> spaces
    ) {
        public SnapshotFloor {
            corridors = corridors == null ? List.of() : List.copyOf(corridors);
            spaces = spaces == null ? List.of() : List.copyOf(spaces);
        }

        Floor toFloor() {
            return new Floor(code, level, name,
                    status == null ? FloorStatus.UNMAPPED : status,
                    accessibility == null ? Accessibility.UNKNOWN : accessibility,
                    note, gridRows, gridColumns,
                    corridors.stream().map(MapMapper::toCorridor).toList(), 0);
        }
    }

    /** Every building in the snapshot, ordered by code so a run is repeatable. */
    public static List<Building> load() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources(PATTERN);
            return Arrays.stream(files)
                    .map(SurveySnapshot::read)
                    .sorted(Comparator.comparing(Building::code))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + PATTERN, e);
        }
    }

    private static Building read(Resource file) {
        try (InputStream in = file.getInputStream()) {
            return JSON.readValue(in, Building.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the map snapshot " + file.getDescription(), e);
        }
    }
}

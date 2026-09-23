package co.edu.konradlorenz.kapp.map.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A campus building, the wings it is divided into and the floors it contains.
 *
 * <p>{@code code} is the stable business key: every path parameter in the API addresses a
 * building by code, never by {@code id}. A unique index on it is created by
 * {@code V001_MapIndexes}.
 *
 * <p>{@code placeholder} is storage-only metadata and is deliberately NOT part of the
 * published {@code Building} schema, so it never reaches a client. It marks the invented
 * campus seeded while the real one is being surveyed, so nobody mistakes seeded data for a
 * survey of the actual campus.
 *
 * @param aliases other names the building goes by. People call buildings by what they house as
 *                often as by their code, sometimes by several things at once, and the search has
 *                to meet them there
 * @param wings   the arms the building is divided into, in its own words; empty for a building
 *                with one
 * @param floors  ordered by ascending level by the constructor, so readers never have to
 *                sort and the API's "ordered by ascending level" promise cannot drift
 */
@Document(collection = "buildings")
public record BuildingDocument(
        @Id String id,
        String code,
        String name,
        String campus,
        String description,
        List<String> aliases,
        List<Wing> wings,
        List<Floor> floors,
        boolean placeholder,
        Instant createdAt,
        Instant updatedAt
) {

    public BuildingDocument {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        wings = wings == null ? List.of() : List.copyOf(wings);
        floors = floors == null
                ? List.of()
                : floors.stream().sorted(Comparator.comparingDouble(Floor::level)).toList();
    }

    public Optional<Floor> floor(String code) {
        return floors.stream().filter(f -> f.code().equals(code)).findFirst();
    }

    public boolean hasFloor(String code) {
        return floor(code).isPresent();
    }

    public Optional<Wing> wing(String code) {
        return wings.stream().filter(w -> w.code().equals(code)).findFirst();
    }
}

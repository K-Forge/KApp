package co.edu.konradlorenz.kapp.map;

import co.edu.konradlorenz.kapp.map.domain.BuildingRepository;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.domain.SpaceCategory;
import co.edu.konradlorenz.kapp.map.domain.SpaceTypeRepository;
import co.edu.konradlorenz.kapp.map.domain.SpaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves {@code V002_PlaceholderCampusSeed} actually ran and produced what the migration
 * promises: two buildings, the real schedule-service room codes, the alias-hard fixture on
 * 302 exactly as specified, and - just as important - that {@code placeholder} never leaks
 * into a response. A client has no schema field to hide it behind; the only thing keeping it
 * off the wire is {@code MapMapper} translating storage documents into DTOs instead of
 * serialising them directly, and that is a regression a JSON diff would not necessarily
 * catch from the outside without an explicit assertion for it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class SeedMigrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BuildingRepository buildings;

    @Autowired
    private SpaceRepository spaces;

    @Autowired
    private SpaceTypeRepository types;

    private static RequestPostProcessor guest() {
        return jwt()
                .jwt(b -> b.subject("seed-guest").claim("roles", List.of("ROLE_GUEST")))
                .authorities(new SimpleGrantedAuthority("ROLE_GUEST"));
    }

    @Test
    @DisplayName("seeds exactly Bloque A and Bloque B, both flagged placeholder")
    void seedsExpectedBuildingCounts() {
        assertThat(buildings.count()).isEqualTo(2);
        assertThat(buildings.countByPlaceholderIsTrue()).isEqualTo(2);
        assertThat(buildings.existsByCode("A")).isTrue();
        assertThat(buildings.existsByCode("B")).isTrue();
    }

    @Test
    @DisplayName("seeds the real schedule-service room codes plus the fixtures the schematic model needs")
    void seedsExpectedSpaceCounts() {
        assertThat(spaces.count()).isEqualTo(24);
        assertThat(spaces.countByPlaceholderIsTrue()).isEqualTo(24);

        List<String> codes = spaces.findAll().stream().map(SpaceDocument::code).sorted().toList();
        assertThat(codes).containsExactlyInAnyOrder(
                // The codes schedule-service resolves. Carried over unchanged through the
                // move from pinned plans to a grid: a room does not stop being room 708
                // because the map is drawn differently.
                "302", "612", "708", "709", "710", "711", "101", "102", "205",
                // Circulation, which is what makes accessVia answerable.
                "ASC-CENTRAL", "ESC-NORTE", "ESC-SUR", "ENT-PRINCIPAL", "B-ASC", "B-ESC",
                // The three wings sharing one base code.
                "301", "301-N", "301-S",
                // A multi-cell room, the basement, and a terrace.
                "310", "S-01", "S-02", "210",
                // A dependency with nothing on its door, and one known but not yet placed.
                "B-MEZZ-01", "B-P3-01");
    }

    @Test
    @DisplayName("the three wings are three rooms sharing one base code")
    void wingsShareABaseCode() {
        List<SpaceDocument> wings = spaces.findAll().stream()
                .filter(s -> "301".equals(s.baseCode()))
                .toList();

        assertThat(wings).hasSize(3);
        assertThat(wings).extracting(SpaceDocument::code)
                .containsExactlyInAnyOrder("301", "301-N", "301-S");
        assertThat(wings).extracting(SpaceDocument::wing)
                .containsExactlyInAnyOrder("C", "N", "S");
    }

    @Test
    @DisplayName("a door code whose ending is not a wing's suffix keeps its whole code as its base code")
    void nonWingSuffixesAreNotStripped() {
        SpaceDocument basement = spaces.findByCodeOrderByBuildingCodeAsc("S-01").get(0);
        SpaceDocument stairs = spaces.findByCodeOrderByBuildingCodeAsc("ESC-SUR").get(0);

        // "S-01" is a basement bay, not a room in the south wing: only the "-S" the south wing
        // declares is stripped, and "S-01" does not end in it.
        assertThat(basement.baseCode()).isEqualTo("S-01");
        // A staircase has nothing on its door. Its code is an identifier, so no door code and no
        // base code - and it still belongs to the south wing, as a field.
        assertThat(stairs.doorCode()).isNull();
        assertThat(stairs.baseCode()).isNull();
        assertThat(stairs.wing()).isEqualTo("S");
    }

    @Test
    @DisplayName("the basement is floor S1 at level -1, and its rooms are reachable")
    void basementIsAtMinusOne() {
        SpaceDocument parking = spaces.findByCodeOrderByBuildingCodeAsc("S-01").get(0);

        assertThat(parking.floorCode()).isEqualTo("S1");
        assertThat(parking.floorLevel()).isEqualTo(-1);
        assertThat(buildings.findByCode("A").orElseThrow().hasFloor("S1")).isTrue();
    }

    @Test
    @DisplayName("the type catalogue keeps every code the old enum had, each in a category")
    void catalogueKeepsTheLegacyCodes() {
        assertThat(types.findAll()).extracting(t -> t.code()).contains(
                "CLASSROOM", "LAB", "AUDITORIUM", "LIBRARY", "CAFETERIA", "RESTROOM", "OFFICE",
                "ADMIN_OFFICE", "WELLBEING", "TERRACE", "ELEVATOR", "STAIRS", "CORRIDOR",
                "ENTRANCE", "OTHER");
        assertThat(types.findById("ELEVATOR").orElseThrow().category()).isEqualTo(SpaceCategory.CIRCULATION);
        assertThat(types.findById("OTHER").orElseThrow().category()).isEqualTo(SpaceCategory.OTHER);
    }

    @Test
    @DisplayName("every seeded space has a type the catalogue knows")
    void everySpaceHasAKnownType() {
        var known = types.findAll().stream().map(t -> t.code()).toList();
        assertThat(spaces.findAll()).allSatisfy(space ->
                assertThat(known).as(space.code()).contains(space.typeCode()));
    }

    @Test
    @DisplayName("every space that is not itself circulation says which lift or staircase serves it")
    void spacesCarryAccessVia() {
        List<SpaceDocument> rooms = spaces.findAll().stream()
                .filter(s -> !List.of("ELEVATOR", "STAIRS", "ENTRANCE").contains(s.typeCode()))
                .toList();

        assertThat(rooms).isNotEmpty();
        assertThat(rooms).allSatisfy(space ->
                assertThat(space.accessVia())
                        .as("%s must say how to reach it - that is what produces "
                                + "\"piso 3, sube por el ascensor central\"", space.code())
                        .isNotNull());
    }

    @Test
    @DisplayName("room 302 carries the exact mismatched-alias fixture the brief specified")
    void room302HasTheSpecifiedAliases() {
        SpaceDocument room302 = spaces.findByCodeOrderByBuildingCodeAsc("302").get(0);

        assertThat(room302.name()).isEqualTo("Laboratorio de Sistemas");
        assertThat(room302.aliases()).containsExactly(
                "sala de sistemas", "lab 302", "laboratorio 302");
    }

    @Test
    @DisplayName("the placeholder flag never appears in an API response")
    void placeholderFlagNeverLeaksToTheApi() throws Exception {
        mockMvc.perform(get("/api/map/buildings/A").with(guest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeholder").doesNotExist());

        mockMvc.perform(get("/api/map/spaces/302").with(guest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeholder").doesNotExist());
    }
}

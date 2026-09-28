package co.edu.konradlorenz.kapp.map.web;

import co.edu.konradlorenz.kapp.map.service.BuildingService;
import co.edu.konradlorenz.kapp.map.service.GroundService;
import co.edu.konradlorenz.kapp.map.service.StructureService;
import co.edu.konradlorenz.kapp.map.web.dto.CampusSummaryResponse;
import co.edu.konradlorenz.kapp.map.web.dto.GroundResponse;
import co.edu.konradlorenz.kapp.map.web.dto.StructuresRequest;
import co.edu.konradlorenz.kapp.map.web.dto.StructuresResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The campus (Sede) list, which clients call first to populate the campus selector; the ground
 * around each campus; and what else stands on its blocks.
 *
 * <p>The list is derived from the buildings rather than stored: a campus exists precisely when a
 * building is on it, so there is no second collection to keep in step.
 */
@RestController
@RequestMapping("/api/map/campuses")
@Tag(name = "Campuses", description = "The list of campuses (Sedes) known to the map.")
public class CampusController {

    private final BuildingService buildings;
    private final GroundService ground;
    private final StructureService structures;

    public CampusController(BuildingService buildings, GroundService ground, StructureService structures) {
        this.buildings = buildings;
        this.ground = ground;
        this.structures = structures;
    }

    @GetMapping
    @Operation(summary = "List campuses",
            description = "Every campus with at least one building, ordered by name. "
                    + "Allowed roles: ROLE_GUEST, ROLE_STUDENT, ROLE_PROFESSOR, ROLE_ADMIN.")
    public List<CampusSummaryResponse> list() {
        return buildings.listCampuses();
    }

    @GetMapping("/{campus}/ground")
    @Operation(summary = "The ground around a campus",
            description = "Its city blocks, sidewalks, roadways and streets, from the city's reference map, "
                    + "for drawing under the buildings. 404 for a campus with none. "
                    + "Allowed roles: ROLE_GUEST, ROLE_STUDENT, ROLE_PROFESSOR, ROLE_ADMIN.")
    public GroundResponse ground(@PathVariable String campus) {
        return ground.forCampus(campus);
    }

    @GetMapping("/{campus}/structures")
    @Operation(summary = "What else stands on a campus's blocks",
            description = "Everything on the campus's blocks that is not the university's - a neighbour's building, "
                    + "a heritage house with its garden - for drawing around the buildings. An empty list, at "
                    + "version 0, while nothing has been saved. "
                    + "Allowed roles: ROLE_GUEST, ROLE_STUDENT, ROLE_PROFESSOR, ROLE_ADMIN.")
    public StructuresResponse structures(@PathVariable String campus) {
        return structures.forCampus(campus);
    }

    @PutMapping("/{campus}/structures")
    @Operation(summary = "Replace what else stands on a campus's blocks",
            description = "The whole list, with the version it was read at. 409 when somebody saved it since, "
                    + "and nothing is changed. Allowed roles: ROLE_ADMIN.")
    public StructuresResponse replaceStructures(@PathVariable String campus,
                                                @Valid @RequestBody StructuresRequest request) {
        return structures.replace(campus, request);
    }
}

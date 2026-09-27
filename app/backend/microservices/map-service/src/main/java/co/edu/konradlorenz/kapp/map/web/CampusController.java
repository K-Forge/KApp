package co.edu.konradlorenz.kapp.map.web;

import co.edu.konradlorenz.kapp.map.service.BuildingService;
import co.edu.konradlorenz.kapp.map.service.GroundService;
import co.edu.konradlorenz.kapp.map.web.dto.CampusSummaryResponse;
import co.edu.konradlorenz.kapp.map.web.dto.GroundResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The campus (Sede) list, which clients call first to populate the campus selector.
 *
 * <p>Derived from the buildings rather than stored: a campus exists precisely when a
 * building is on it, so there is no second collection to keep in step.
 */
@RestController
@RequestMapping("/api/map/campuses")
@Tag(name = "Campuses", description = "The list of campuses (Sedes) known to the map.")
public class CampusController {

    private final BuildingService buildings;
    private final GroundService ground;

    public CampusController(BuildingService buildings, GroundService ground) {
        this.buildings = buildings;
        this.ground = ground;
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
}

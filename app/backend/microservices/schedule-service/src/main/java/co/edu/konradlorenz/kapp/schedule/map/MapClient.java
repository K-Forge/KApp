package co.edu.konradlorenz.kapp.schedule.map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Reads the campus buildings from {@code map-service}, which owns them: each building names the
 * sedes SINU gives it ({@code Building.sinuSedes}, map 3.6).
 *
 * <p>Addressed by {@code kapp.schedule.map-service-url}, map-service's name by default. The caller's
 * own token goes along, added by {@code common}'s {@code KappFeignAutoConfiguration}: every member
 * may read the map. See {@link SedeBuildings} for how a failure is handled.
 */
@FeignClient(name = "map-service", url = "${kapp.schedule.map-service-url}", path = "/api/map")
public interface MapClient {

    @GetMapping("/buildings")
    List<MapBuildingView> listBuildings();
}

package co.edu.konradlorenz.kapp.schedule.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * The building of each sede, as the map says: in SINU each building is a sede, and a class's room is
 * named by its sede and its number, so a client needs the building to open the room on the map.
 *
 * <p>Cached a few minutes, and only when the map answered something: a sede mapped in the portal
 * shows up on the next timetable after that. With map-service down, the timetable is still served,
 * every {@code buildingCode} null, which is what the contract says for a sede nobody has mapped.
 */
@Service
public class SedeBuildings {

    private static final Logger log = LoggerFactory.getLogger(SedeBuildings.class);

    private final MapClient map;

    public SedeBuildings(MapClient map) {
        this.map = map;
    }

    /** @return the building code of each sede, by the sede as SINU writes it */
    @Cacheable(cacheNames = "sedeBuildings", unless = "#result.isEmpty()")
    public Map<String, String> bySede() {
        try {
            Map<String, String> buildings = new HashMap<>();
            for (MapBuildingView building : map.listBuildings()) {
                building.sinuSedes().forEach(sede -> buildings.putIfAbsent(sede.trim(), building.code()));
            }
            return buildings;
        } catch (RuntimeException e) {
            log.warn("Could not read the buildings from map-service; serving the timetable without them", e);
            return Map.of();
        }
    }
}

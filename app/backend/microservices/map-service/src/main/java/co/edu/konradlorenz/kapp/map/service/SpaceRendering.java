package co.edu.konradlorenz.kapp.map.service;

import co.edu.konradlorenz.kapp.map.domain.BuildingDocument;
import co.edu.konradlorenz.kapp.map.domain.BuildingRepository;
import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import co.edu.konradlorenz.kapp.map.domain.SpaceTypeDocument;
import co.edu.konradlorenz.kapp.map.domain.SpaceTypeRepository;
import co.edu.konradlorenz.kapp.map.web.dto.SpaceResponse;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Renders a batch of spaces with what each one needs from elsewhere: its type's name and
 * category, and its floor's accessibility.
 *
 * <p>A search page can span several buildings, so the lookups are done once per batch - one
 * read of the type catalogue, one read of the buildings on the page - rather than once per
 * space. The catalogue is a few dozen documents and a campus a dozen buildings; neither is worth
 * a cache that could go stale.
 */
@Component
public class SpaceRendering {

    private final SpaceTypeRepository types;
    private final BuildingRepository buildings;

    public SpaceRendering(SpaceTypeRepository types, BuildingRepository buildings) {
        this.types = types;
        this.buildings = buildings;
    }

    public List<SpaceResponse> render(List<SpaceDocument> spaces) {
        if (spaces.isEmpty()) {
            return List.of();
        }
        Map<String, SpaceTypeDocument> typesByCode = typesByCode();
        Set<String> buildingIds = spaces.stream().map(SpaceDocument::buildingId).collect(Collectors.toSet());
        Map<String, BuildingDocument> buildingsById = new HashMap<>();
        buildings.findAllById(buildingIds).forEach(b -> buildingsById.put(b.id(), b));

        return spaces.stream()
                .map(space -> {
                    BuildingDocument building = buildingsById.get(space.buildingId());
                    return MapMapper.toSpaceResponse(space, typesByCode.get(space.typeCode()),
                            building == null ? null : building.floor(space.floorCode()).orElse(null));
                })
                .toList();
    }

    /** Spaces known to share one building, as a floor read has them. */
    public List<SpaceResponse> render(List<SpaceDocument> spaces, BuildingDocument building) {
        Map<String, SpaceTypeDocument> typesByCode = typesByCode();
        return spaces.stream()
                .map(space -> MapMapper.toSpaceResponse(space, typesByCode.get(space.typeCode()),
                        building.floor(space.floorCode()).orElse(null)))
                .toList();
    }

    public Map<String, SpaceTypeDocument> typesByCode() {
        return types.findAll().stream()
                .collect(Collectors.toMap(SpaceTypeDocument::code, Function.identity()));
    }
}

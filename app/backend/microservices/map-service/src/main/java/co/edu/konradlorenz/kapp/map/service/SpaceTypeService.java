package co.edu.konradlorenz.kapp.map.service;

import co.edu.konradlorenz.kapp.common.error.DuplicateResourceException;
import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.map.domain.SpaceRepository;
import co.edu.konradlorenz.kapp.map.domain.SpaceTypeDocument;
import co.edu.konradlorenz.kapp.map.domain.SpaceTypeRepository;
import co.edu.konradlorenz.kapp.map.web.dto.SpaceTypeRequest;
import co.edu.konradlorenz.kapp.map.web.dto.SpaceTypeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * The space type catalogue: what the portal adds to when the survey finds a kind of room nobody
 * listed.
 *
 * <p>A type's code never changes once created, because spaces store it; its name and category
 * do. Deleting one that any space still uses is refused rather than cascaded, for the same reason
 * a building with spaces cannot be deleted: whoever pressed the button should not find out weeks
 * later what else went with it.
 */
@Service
public class SpaceTypeService {

    private static final Logger log = LoggerFactory.getLogger(SpaceTypeService.class);

    private final SpaceTypeRepository types;
    private final SpaceRepository spaces;

    public SpaceTypeService(SpaceTypeRepository types, SpaceRepository spaces) {
        this.types = types;
        this.spaces = spaces;
    }

    /** Grouped by category, in the category's declared order, then by name. */
    public List<SpaceTypeResponse> list() {
        return types.findAll().stream()
                .sorted(Comparator.comparing((SpaceTypeDocument t) -> t.category().ordinal())
                        .thenComparing(SpaceTypeDocument::name, String.CASE_INSENSITIVE_ORDER))
                .map(MapMapper::toSpaceTypeResponse)
                .toList();
    }

    public SpaceTypeResponse create(SpaceTypeRequest request) {
        if (types.existsById(request.code())) {
            throw new DuplicateResourceException("Space type", request.code());
        }
        Instant now = Instant.now();
        SpaceTypeDocument saved = types.save(new SpaceTypeDocument(
                request.code(), request.name().trim(), request.category(), now, now));
        log.info("Created space type {} ({})", saved.code(), saved.category());
        return MapMapper.toSpaceTypeResponse(saved);
    }

    public SpaceTypeResponse update(String code, SpaceTypeRequest request) {
        SpaceTypeDocument existing = require(code);
        SpaceTypeDocument saved = types.save(new SpaceTypeDocument(
                existing.code(), request.name().trim(), request.category(),
                existing.createdAt(), Instant.now()));
        return MapMapper.toSpaceTypeResponse(saved);
    }

    public void delete(String code) {
        SpaceTypeDocument existing = require(code);
        if (spaces.existsByTypeCode(code)) {
            throw new MapConflictException(
                    "Space type %s is still used by at least one space. Change those spaces first."
                            .formatted(code));
        }
        types.delete(existing);
        log.info("Deleted space type {}", code);
    }

    private SpaceTypeDocument require(String code) {
        return types.findById(code).orElseThrow(() -> new ResourceNotFoundException("Space type", code));
    }
}

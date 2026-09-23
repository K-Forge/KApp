package co.edu.konradlorenz.kapp.map.web;

import co.edu.konradlorenz.kapp.map.domain.SpaceCategory;
import co.edu.konradlorenz.kapp.map.service.SpaceService;
import co.edu.konradlorenz.kapp.map.web.dto.PageResponse;
import co.edu.konradlorenz.kapp.map.web.dto.SpaceDetailResponse;
import co.edu.konradlorenz.kapp.map.web.dto.SpaceRequest;
import co.edu.konradlorenz.kapp.map.web.dto.SpaceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Spaces: the campus-wide search, and the room-code lookup.
 *
 * <p>{@code /search} is declared before {@code /{code}} for the reader's benefit only -
 * Spring's path matching already prefers the literal segment over the template, so the two
 * cannot collide.
 *
 * <p>{@code page} and {@code size} are validated, not clamped. A client asking for 5000
 * rows is told 400 rather than handed 100 and left to believe that was everything; the
 * settled decision is recorded in {@code docs/INTEGRATION-NOTES.md}.
 */
@RestController
@RequestMapping("/api/map/spaces")
@Validated
@Tag(name = "Spaces",
        description = "Rooms, labs, offices and other locatable spaces, plus the campus-wide search.")
public class SpaceController {

    private final SpaceService spaces;

    public SpaceController(SpaceService spaces) {
        this.spaces = spaces;
    }

    @GetMapping("/search")
    @Operation(summary = "Search or list spaces across the campus",
            description = "With q: matches it against the door code, the name and the aliases, "
                    + "case- and accent-insensitively, ordered by descending relevance. "
                    + "Without q: lists every space the filters allow, ordered by building, "
                    + "floor and code - which is how a floor is managed rather than how a "
                    + "student finds a room. type takes a type code, category a whole category. "
                    + "Allowed roles: ROLE_GUEST, ROLE_STUDENT, ROLE_PROFESSOR, ROLE_ADMIN.")
    public PageResponse<SpaceResponse> search(
            // Optional, because "show me everything in building A" is a real question and the
            // admin portal had no way to ask it: with no term the screen could not list what
            // you had just created, and the building and type filters did nothing on their own.
            @RequestParam(required = false) @Size(min = 2, max = 100) String q,
            @RequestParam(required = false) @Size(min = 1, max = 120) String campus,
            @RequestParam(required = false) @Size(min = 1, max = 40) String type,
            @RequestParam(required = false) SpaceCategory category,
            @RequestParam(required = false) @Size(min = 1, max = 10) String buildingCode,
            @RequestParam(required = false) @Size(min = 1, max = 8) String wing,
            @RequestParam(required = false) @Size(min = 1, max = 8) String floor,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        return spaces.search(q, campus, type, category, buildingCode, wing, floor, page, size);
    }

    @GetMapping("/{code}")
    @Operation(summary = "Resolve a room code to its space, floor and building",
            description = "The endpoint the schedule screen calls when a student taps a class. "
                    + "Pass buildingCode when the same room code exists in more than one "
                    + "building, otherwise an ambiguous code is answered with 409. "
                    + "Allowed roles: ROLE_GUEST, ROLE_STUDENT, ROLE_PROFESSOR, ROLE_ADMIN.")
    public SpaceDetailResponse get(
            @PathVariable @Size(min = 1, max = 20) String code,
            @RequestParam(required = false) @Size(min = 1, max = 10) String buildingCode) {
        return spaces.getDetail(code, buildingCode);
    }

    @PostMapping
    @Operation(summary = "Create a space", description = "Allowed roles: ROLE_ADMIN only.")
    public ResponseEntity<SpaceResponse> create(@Valid @RequestBody SpaceRequest request) {
        SpaceResponse created = spaces.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/map/spaces/{code}")
                        .queryParam("buildingCode", created.buildingCode())
                        .buildAndExpand(created.code()).toUri())
                .body(created);
    }

    @PutMapping("/{code}")
    @Operation(summary = "Update a space",
            description = "Moving a space is done here, by sending a new building, floor or grid "
                    + "cell. Allowed roles: ROLE_ADMIN only.")
    public SpaceResponse update(
            @PathVariable @Size(min = 1, max = 20) String code,
            @RequestParam(required = false) @Size(min = 1, max = 10) String buildingCode,
            @Valid @RequestBody SpaceRequest request) {
        return spaces.update(code, buildingCode, request);
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Delete a space",
            description = "Refused with 409 while other spaces name it as their accessVia. "
                    + "Allowed roles: ROLE_ADMIN only.")
    public ResponseEntity<Void> delete(
            @PathVariable @Size(min = 1, max = 20) String code,
            @RequestParam(required = false) @Size(min = 1, max = 10) String buildingCode) {
        spaces.delete(code, buildingCode);
        return ResponseEntity.noContent().build();
    }
}

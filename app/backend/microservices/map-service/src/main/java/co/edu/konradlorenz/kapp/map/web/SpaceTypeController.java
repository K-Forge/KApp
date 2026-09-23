package co.edu.konradlorenz.kapp.map.web;

import co.edu.konradlorenz.kapp.map.service.SpaceTypeService;
import co.edu.konradlorenz.kapp.map.web.dto.SpaceTypeRequest;
import co.edu.konradlorenz.kapp.map.web.dto.SpaceTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * The space type catalogue. Readable by anyone holding a token, because every client needs the
 * names; writable by administrators, because a type is shared by the whole campus.
 */
@RestController
@RequestMapping("/api/map/space-types")
@Validated
@Tag(name = "Space types", description = "The catalogue of kinds of space, each in a fixed category.")
public class SpaceTypeController {

    private static final String CODE = "^[A-Z][A-Z0-9_]{1,39}$";

    private final SpaceTypeService types;

    public SpaceTypeController(SpaceTypeService types) {
        this.types = types;
    }

    @GetMapping
    @Operation(summary = "List space types",
            description = "Grouped by category, then by name. "
                    + "Allowed roles: ROLE_GUEST, ROLE_STUDENT, ROLE_PROFESSOR, ROLE_ADMIN.")
    public List<SpaceTypeResponse> list() {
        return types.list();
    }

    @PostMapping
    @Operation(summary = "Create a space type", description = "Allowed roles: ROLE_ADMIN only.")
    public ResponseEntity<SpaceTypeResponse> create(@Valid @RequestBody SpaceTypeRequest request) {
        SpaceTypeResponse created = types.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/map/space-types/{code}")
                        .buildAndExpand(created.code()).toUri())
                .body(created);
    }

    @PutMapping("/{code}")
    @Operation(summary = "Rename a space type or move it to another category",
            description = "The code never changes: spaces store it. Allowed roles: ROLE_ADMIN only.")
    public SpaceTypeResponse update(@PathVariable @Pattern(regexp = CODE) String code,
                                    @Valid @RequestBody SpaceTypeRequest request) {
        return types.update(code, request);
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Delete a space type",
            description = "Refused with 409 while any space uses it. Allowed roles: ROLE_ADMIN only.")
    public ResponseEntity<Void> delete(@PathVariable @Pattern(regexp = CODE) String code) {
        types.delete(code);
        return ResponseEntity.noContent().build();
    }
}

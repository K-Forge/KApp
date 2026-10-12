package co.edu.konradlorenz.kapp.auth.web;

import co.edu.konradlorenz.kapp.auth.service.VisitorPassService;
import co.edu.konradlorenz.kapp.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * The reception counter: minting day passes and reading the register of who used them.
 *
 * <p>{@code ROLE_RECEPTION} or {@code ROLE_ADMIN} throughout, enforced per method rather than on
 * the class, so a later addition to this controller cannot inherit an assumption nobody re-reads.
 *
 * <p><strong>Everything here returns personal data.</strong> The listing carries visitors'
 * names and identity documents, which is the whole point of a register and also the reason
 * this controller lives behind {@code /auth/admin/**} and nothing else exposes it. See
 * {@code SECURITY-AUDIT.md}, S12.
 */
@RestController
@RequestMapping("/auth/admin/visitor-passes")
@Tag(name = "Visitor passes")
public class AdminVisitorPassController {

    private final VisitorPassService passes;

    public AdminVisitorPassController(VisitorPassService passes) {
        this.passes = passes;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTION')")
    @Operation(summary = "List visitor passes",
            description = "Newest first. Filter by whether they have been redeemed. "
                    + "Includes visitors' identity documents; records disappear after 30 days.")
    public List<VisitorPassResponse> listVisitorPasses(
            @RequestParam(required = false) Boolean redeemed) {
        return passes.list(redeemed).stream().map(VisitorPassResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTION')")
    @Operation(summary = "Issue a visitor day pass",
            description = "Returns a code to read out to the visitor. Redeemable for 12 hours; "
                    + "once redeemed the visitor has 24 hours of map-only access.")
    public ResponseEntity<VisitorPassResponse> createVisitorPass(
            @Valid @RequestBody(required = false) VisitorPassRequest body) {
        var created = passes.issue(CurrentUser.id(), body == null ? null : body.notes());
        return ResponseEntity
                .created(URI.create("/auth/admin/visitor-passes/" + created.code()))
                .body(VisitorPassResponse.from(created));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTION')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke an unredeemed pass",
            description = "409 for a pass that has been redeemed: that row is the record of a "
                    + "visit, not a pass, and it leaves on its own after 30 days.")
    public void deleteVisitorPass(@PathVariable String code) {
        passes.revoke(code);
    }
}

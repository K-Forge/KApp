package co.edu.konradlorenz.kapp.auth.web;

import co.edu.konradlorenz.kapp.auth.service.VisitorPassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where a visitor exchanges the code reception gave them for access.
 *
 * <p>Public, necessarily: a visitor has no account and nothing to authenticate with. The
 * code is the credential, which is why it is short-lived, single-use and rate-limited at the
 * gateway alongside login and registration.
 */
@RestController
@RequestMapping("/auth/visitor-passes")
@Tag(name = "Visitor passes")
public class VisitorPassController {

    private final VisitorPassService passes;

    public VisitorPassController(VisitorPassService passes) {
        this.passes = passes;
    }

    @PostMapping("/{code}/redeem")
    @Operation(summary = "Redeem a visitor day pass",
            description = "Records the visitor's identity document and returns a token valid for "
                    + "24 hours that opens the campus map and nothing else.")
    public AuthController.TokenResponse redeemVisitorPass(@PathVariable String code,
                                            @Valid @RequestBody VisitorPassRedemptionRequest body) {
        var issued = passes.redeem(code, body);
        // A pass is for today: no refresh token, and both of its fields null.
        return AuthController.TokenResponse.withoutRefresh(issued);
    }
}

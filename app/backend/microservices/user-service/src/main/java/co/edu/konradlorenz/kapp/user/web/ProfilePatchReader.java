package co.edu.konradlorenz.kapp.user.web;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.user.web.dto.ProfilePatch;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the raw JSON of {@code PATCH /api/users/me} into a {@link ProfilePatch}.
 *
 * <p>The endpoint binds a {@link JsonNode} rather than a record so that every field the client sent
 * can be seen and named: {@code avatarUrl} is the only one a person changes, and anything else is
 * refused with a reason rather than silently dropped. A client that PATCHes back the whole profile
 * it just read should be told which fields it may not send, and why.
 */
@Component
public class ProfilePatchReader {

    private static final String AVATAR_URL = "avatarUrl";
    private static final int AVATAR_URL_MAX = 500;

    public ProfilePatch read(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new BusinessRuleException("The request body must be a JSON object");
        }

        List<ApiError.FieldIssue> issues = new ArrayList<>();
        // LinkedHashSet so the order the client sent the fields in is the order it reads them
        // back, which makes a long error easier to match up against the request.
        Set<String> names = new LinkedHashSet<>();
        body.fieldNames().forEachRemaining(names::add);
        for (String name : names) {
            if (!AVATAR_URL.equals(name)) {
                issues.add(new ApiError.FieldIssue(name, notEditable(name)));
            }
        }

        String avatarUrl = null;
        JsonNode node = body.get(AVATAR_URL);
        if (node == null) {
            issues.add(new ApiError.FieldIssue(AVATAR_URL, "is required: send a URL, or null to clear it"));
        } else if (!node.isNull()) {
            if (!node.isTextual()) {
                issues.add(new ApiError.FieldIssue(AVATAR_URL, "must be a string"));
            } else if (node.textValue().length() > AVATAR_URL_MAX) {
                issues.add(new ApiError.FieldIssue(AVATAR_URL, "must be at most " + AVATAR_URL_MAX + " characters"));
            } else if (!isAbsoluteUrl(node.textValue())) {
                issues.add(new ApiError.FieldIssue(AVATAR_URL, "must be an absolute URL"));
            } else {
                avatarUrl = node.textValue();
            }
        }

        if (!issues.isEmpty()) {
            throw new BusinessRuleException("Validation failed", issues);
        }
        return new ProfilePatch(avatarUrl);
    }

    private static String notEditable(String field) {
        return switch (field) {
            case "firstName", "lastName" -> "comes from Microsoft at every sign-in and cannot be changed here";
            case "email" -> "is owned by the authentication service and cannot be changed here";
            case "roles", "role" -> "is granted by the authentication service and cannot be changed here";
            case "active" -> "is changed through PATCH /api/users/{userId}/status";
            case "academic" -> "is read from SINU and cannot be changed here";
            case "identification", "phone" -> "is not kept: KApp stores no identity document or phone number";
            case "id" -> "is assigned by the server and cannot be changed";
            default -> "is not a field of this resource";
        };
    }

    private static boolean isAbsoluteUrl(String value) {
        try {
            return new URI(value).isAbsolute();
        } catch (URISyntaxException e) {
            return false;
        }
    }
}

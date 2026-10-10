package co.edu.konradlorenz.kapp.schedule.map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The slice of map-service's {@code Building} this service needs: its code, and the names SINU gives
 * it as a sede. Everything else - floors, outlines, wings - is ignored, which is also why
 * {@code ignoreUnknown} is required.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MapBuildingView(String code, List<String> sinuSedes) {

    public MapBuildingView {
        sinuSedes = sinuSedes == null ? List.of() : List.copyOf(sinuSedes);
    }
}

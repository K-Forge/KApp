package co.edu.konradlorenz.kapp.map.service;

import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import co.edu.konradlorenz.kapp.map.web.dto.GroundResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The ground around each campus, as the files under {@code db/ground/} carry it.
 *
 * <p>Reference data rather than something the portal edits: the blocks, sidewalks and roadways are
 * the city's, cut from its reference map by {@code scripts/map-ground.py} and shipped with the
 * service. Read once, on the first request.
 */
@Service
public class GroundService {

    static final String PATTERN = "classpath*:db/ground/*.json";

    private final ObjectMapper json;
    private volatile Map<String, GroundResponse> byCampus;

    public GroundService(ObjectMapper json) {
        this.json = json;
    }

    public GroundResponse forCampus(String campus) {
        GroundResponse ground = all().get(key(campus));
        if (ground == null) {
            throw new ResourceNotFoundException("Ground of campus", campus);
        }
        return ground;
    }

    private Map<String, GroundResponse> all() {
        Map<String, GroundResponse> loaded = byCampus;
        if (loaded == null) {
            loaded = load();
            byCampus = loaded;
        }
        return loaded;
    }

    private Map<String, GroundResponse> load() {
        Map<String, GroundResponse> grounds = new LinkedHashMap<>();
        try {
            for (Resource file : new PathMatchingResourcePatternResolver().getResources(PATTERN)) {
                try (InputStream in = file.getInputStream()) {
                    GroundResponse ground = json.readValue(in, GroundResponse.class);
                    grounds.put(key(ground.campus()), ground);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The ground files under db/ground/ could not be read", e);
        }
        return Map.copyOf(grounds);
    }

    /** Campus names match ignoring case and accents, as the building search does. */
    private static String key(String campus) {
        return Normalizer.normalize(campus.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}

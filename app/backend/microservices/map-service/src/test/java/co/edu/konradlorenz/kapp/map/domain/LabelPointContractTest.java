package co.edu.konradlorenz.kapp.map.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The contract's floor example and the service agree on every label point.
 *
 * <p>The mocks serve the example, so a client tested against them draws the labels where the
 * service will put them. When the algorithm changes, the example changes with it.
 */
class LabelPointContractTest {

    /** docs/api, from the module's directory, where Maven runs the tests. */
    private static final Path CONTRACT = Path.of("..", "..", "..", "..", "docs", "api", "map.openapi.yaml");

    @Test
    @DisplayName("each label point in the CentralGroundFloor example is the one the service computes")
    @SuppressWarnings("unchecked")
    void exampleMatchesTheService() throws IOException {
        Map<String, Object> contract;
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(10 * 1024 * 1024);
        try (Reader reader = Files.newBufferedReader(CONTRACT)) {
            contract = new Yaml(options).load(reader);
        }
        Map<String, Object> examples = (Map<String, Object>) ((Map<String, Object>) contract.get("components")).get("examples");
        Map<String, Object> floor = (Map<String, Object>) ((Map<String, Object>) examples.get("CentralGroundFloor")).get("value");
        List<Map<String, Object>> spaces = (List<Map<String, Object>>) floor.get("spaces");

        int checked = 0;
        for (Map<String, Object> space : spaces) {
            List<Map<String, Object>> shape = (List<Map<String, Object>>) space.get("shape");
            if (shape == null) {
                assertThat(space).as(space.get("code") + " has no shape").doesNotContainKey("labelPoint");
                continue;
            }
            Point computed = LabelPoint.of(shape.stream().map(LabelPointContractTest::point).toList());
            assertThat(point((Map<String, Object>) space.get("labelPoint"))).as((String) space.get("code"))
                    .isEqualTo(computed);
            checked++;
        }
        assertThat(checked).isEqualTo(42);
    }

    private static Point point(Map<String, Object> p) {
        return new Point((Integer) p.get("x"), (Integer) p.get("y"));
    }
}

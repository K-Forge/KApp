package co.edu.konradlorenz.kapp.semaphore.migration;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The semaphore 2.0 change units, on documents written the way 1.0 wrote them: a pensum whose items
 * carry a printed code, Estadística Descriptiva under its printed code, and a plan whose placements
 * are named by {@code code}.
 */
@SpringBootTest
@Testcontainers
class SemaphoreTwoMigrationsTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MongoTemplate mongo;

    @Test
    @DisplayName("V008 takes the printed code off every item, and points a prerequisite that named one at its item")
    void printedCodesGo() {
        mongo.getCollection("pensums").insertOne(new Document("_id", "OLD-1").append("courses", new ArrayList<>(List.of(
                new Document("code", "C-1").append("pensumItemCode", "I-1").append("prerequisites", List.of()),
                new Document("code", null).append("pensumItemCode", "SLOT").append("prerequisites", List.of()),
                new Document("code", "C-2").append("pensumItemCode", "I-2").append("prerequisites", List.of("C-1"))))));

        new V008_PensumItemsWithoutPrintedCode().execute(mongo);
        new V008_PensumItemsWithoutPrintedCode().execute(mongo);

        List<Document> courses = mongo.getCollection("pensums").find(new Document("_id", "OLD-1")).first()
                .getList("courses", Document.class);
        assertThat(courses).allSatisfy(c -> assertThat(c.containsKey("code")).isFalse());
        assertThat(courses.get(2).getList("prerequisites", String.class)).containsExactly("I-1");
    }

    @Test
    @DisplayName("V009 gives Estadística Descriptiva SINU's 17080 once, and its rollback gives the printed one back")
    void estadisticaDescriptiva() {
        // The seed already carries 17080; put the printed code back to see the change unit do its work.
        V009_EstadisticaDescriptivaAsSinuHasIt.set(mongo, "17080", "17018");

        assertThat(V009_EstadisticaDescriptivaAsSinuHasIt.set(mongo, "17018", "17080")).isTrue();
        assertThat(V009_EstadisticaDescriptivaAsSinuHasIt.set(mongo, "17018", "17080")).isFalse();
        assertThat(sinuCodeOf("17018")).isEqualTo("17080");
        assertThat(sinuCodeOf("17070")).isEqualTo("17070");
    }

    @Test
    @DisplayName("V010 names placements by pensumItemCode with no chosen elective, and its rollback undoes it")
    void placements() {
        mongo.getCollection("academicPlans").insertOne(new Document("_id", "plan-old").append("placements", List.of(
                new Document("code", "11015").append("plannedLevel", 3))));

        new V010_PlacementsByPensumItemCode().execute(mongo);
        Document placement = placementOf("plan-old");
        assertThat(placement.getString("pensumItemCode")).isEqualTo("11015");
        assertThat(placement.getInteger("plannedLevel")).isEqualTo(3);
        assertThat(placement.containsKey("electiveSinuCode")).isTrue();
        assertThat(placement.containsKey("code")).isFalse();

        new V010_PlacementsByPensumItemCode().rollback(mongo);
        assertThat(placementOf("plan-old").getString("code")).isEqualTo("11015");
    }

    private String sinuCodeOf(String pensumItemCode) {
        return mongo.getCollection("pensums").find(new Document("_id", "1015")).first()
                .getList("courses", Document.class).stream()
                .filter(c -> pensumItemCode.equals(c.getString("pensumItemCode")))
                .findFirst().orElseThrow().getString("sinuCode");
    }

    private Document placementOf(String planId) {
        return mongo.getCollection("academicPlans").find(new Document("_id", planId)).first()
                .getList("placements", Document.class).get(0);
    }
}

package co.edu.konradlorenz.kapp.map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The space type catalogue the portal edits. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        // The placeholder campus is these tests' fixture; SurveyedCampusTest covers the survey.
        "kapp.map.survey-seed=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class SpaceTypeCatalogueTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor admin() {
        return jwt().jwt(b -> b.subject("types-admin").claim("roles", List.of("ROLE_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("a new type is usable straight away: the survey finds a room, the portal names it")
    void newTypeIsUsableImmediately() throws Exception {
        mockMvc.perform(post("/api/map/space-types").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "LACTATION_ROOM", "name": "Sala de lactancia", "category": "FACILITIES"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/map/spaces").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "B-P2-LAC", "name": "Sala de lactancia", "typeCode": "LACTATION_ROOM",
                                 "buildingCode": "B", "floorCode": "P2"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeName").value("Sala de lactancia"))
                .andExpect(jsonPath("$.category").value("FACILITIES"));
    }

    @Test
    @DisplayName("a type code that already exists is refused with 409")
    void duplicateCodeIsRefused() throws Exception {
        mockMvc.perform(post("/api/map/space-types").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "CLASSROOM", "name": "Otra aula", "category": "TEACHING"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a type any space uses cannot be deleted")
    void typeInUseCannotBeDeleted() throws Exception {
        mockMvc.perform(delete("/api/map/space-types/CLASSROOM").with(admin()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("renaming a type keeps its code, and every space shows the new name")
    void renamingKeepsTheCode() throws Exception {
        mockMvc.perform(put("/api/map/space-types/TERRACE").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "IGNORED", "name": "Terraza abierta", "category": "SOCIAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TERRACE"));

        mockMvc.perform(get("/api/map/spaces/210").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeName").value("Terraza abierta"));
    }

    @Test
    @DisplayName("the search narrows to a whole category through the types it holds")
    void searchByCategory() throws Exception {
        mockMvc.perform(get("/api/map/spaces/search").param("category", "CIRCULATION")
                        .param("buildingCode", "B").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].category").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("CIRCULATION"))));
    }
}

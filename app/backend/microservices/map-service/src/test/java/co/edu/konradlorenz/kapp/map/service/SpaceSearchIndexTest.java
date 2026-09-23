package co.edu.konradlorenz.kapp.map.service;

import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves, rather than asserts, that {@link SpaceSearch} is served from
 * {@code tx_spaces_search} rather than a collection scan - modelled on
 * {@code user-service}'s {@code DirectorySearchIndexTest}, which is the reference for this
 * kind of test in this codebase.
 *
 * <h2>Why this needs proving instead of trusting the javadoc</h2>
 * An {@code IXSCAN} in a winning plan does not by itself mean a query is cheap. In MongoDB
 * 7, an unanchored, case-insensitive regex against an indexed field also reports
 * {@code IXSCAN} in its plan - it is walking the index, just across its full unbounded key
 * range, which costs the same as reading every document while sounding, from the stage
 * name alone, like it found a shortcut. "Uses the index" only means something once the
 * bounds are checked and {@code totalDocsExamined} is shown to track {@code nReturned}
 * rather than the collection size. That is what {@link #searchExaminesOnlyMatchingDocuments}
 * checks, not just {@link #searchAvoidsACollectionScan}.
 *
 * <p>{@code $text} does not have a regex-shaped failure mode - MongoDB refuses to run a
 * {@code $text} query at all without a text index to serve it, so there is no silent
 * degrade-to-collection-scan path the way there is for a regex. That is precisely why
 * {@code SpaceSearch} is built on {@code $text} rather than a case-insensitive regex; see
 * its class javadoc. These tests exist to keep that guarantee honest as the query gains a
 * sort, a skip/limit and extra filter criteria on top of the bare {@code $text} predicate,
 * any one of which could in principle force an in-memory step over more than the matched
 * documents.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class SpaceSearchIndexTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    private static final int FILLER_SPACES = 50;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private SpaceSearch spaceSearch;

    /**
     * One real, uniquely-matching room plus enough filler that "examined 1 document" and
     * "examined the whole collection" are unmistakably different numbers. The placeholder
     * seed migration also runs in this context, so the true collection size is larger still
     * - the assertions only ever require "small", never an exact total.
     */
    @BeforeEach
    void seedFillerSpaces() {
        Instant now = Instant.now();
        // The (buildingId, code) pair is uniquely indexed, and @BeforeEach reruns for every
        // test method against the SAME Mongo container - so buildingId is randomised per
        // call rather than a fixed "filler-building", or the second test method in this
        // class would fail on a duplicate key rather than on anything it is actually
        // testing.
        String fillerBuildingId = UUID.randomUUID().toString();
        for (int i = 1; i <= FILLER_SPACES; i++) {
            mongoTemplate.save(new SpaceDocument(
                UUID.randomUUID().toString(),
                "FILL" + i,
                "FILL" + i,
                "FILL" + i,
                null,
                "Filler space " + i,
                "OTHER",
                fillerBuildingId,
                "FILL",
                "Sede Test",
                "P1",
                1,
                List.of(),
                1,
                1,
                1,
                1,
                null,
                null,
                null,
                null,
                false,
                now,
                now));
        }
    }

    @Test
    @DisplayName("the filter SpaceSearch builds is answered by the text index, not a collection scan")
    void searchAvoidsACollectionScan() {
        TextQuery filter = SpaceSearch.buildFilter("302", new SpaceSearch.Filters("302", null, null, null, null, null));

        Document plan = winningPlanOf(explain(filter));

        assertThat(plan.toJson())
                .contains("tx_spaces_search")
                .doesNotContain("COLLSCAN");
    }

    @Test
    @DisplayName("the index scan examines only the matching document, not the filler collection")
    void searchExaminesOnlyMatchingDocuments() {
        TextQuery filter = SpaceSearch.buildFilter("302", new SpaceSearch.Filters("302", null, null, null, null, null));

        Document stats = executionStatsOf(explain(filter));

        // Exactly one document carries the term "302" as a whole word (room 302's own code,
        // plus its aliases "lab 302" / "laboratorio 302" - all on the SAME document, so a
        // single document still means a single match). Anything approaching the filler
        // count would mean the text scan degenerated into reading everything.
        assertThat(stats.getInteger("nReturned")).isEqualTo(1);
        assertThat(stats.getInteger("totalDocsExamined")).isEqualTo(1);
        assertThat(stats.getInteger("totalDocsExamined"))
                .as("examined should track returned, not the filler collection size")
                .isEqualTo(stats.getInteger("nReturned"));
    }

    @Test
    @DisplayName("the full paged query (score sort, code sort, skip/limit) still resolves through the index")
    void pagedQueryStillUsesTheIndex() {
        TextQuery filter = SpaceSearch.buildFilter("302", new SpaceSearch.Filters("302", null, null, null, null, null));
        filter.sortByScore();
        filter.with(Sort.by(Sort.Direction.ASC, "code"));
        filter.skip(0).limit(20);

        Document explained = explain(filter);
        Document plan = winningPlanOf(explained);
        Document stats = executionStatsOf(explained);

        assertThat(plan.toJson()).contains("tx_spaces_search").doesNotContain("COLLSCAN");
        assertThat(stats.getInteger("totalDocsExamined")).isEqualTo(1);
    }

    @Test
    @DisplayName("extra filter criteria (campus/type/buildingCode) still resolve through the same index")
    void combinedCriteriaStillUsesTheIndex() {
        TextQuery filter = SpaceSearch.buildFilter("302", new SpaceSearch.Filters("302", "Sede Principal", List.of("LAB"), "A", null, null));

        Document plan = winningPlanOf(explain(filter));

        assertThat(plan.toJson()).contains("tx_spaces_search").doesNotContain("COLLSCAN");
    }

    @Test
    @DisplayName("control: a naive case-insensitive regex on the same fields examines the whole collection")
    void theNaiveRegexAlternativeWouldScanEverything() {
        // The implementation SpaceSearch deliberately does not use - see its class javadoc.
        // Unanchored because "sistemas" can be anywhere in the alias, which is exactly the
        // shape a hand-rolled "search" tends to take.
        Query naive = new Query(Criteria.where("name").regex("sistemas", "i"));

        Document explained = explain(naive);
        Document plan = winningPlanOf(explained);
        Document stats = executionStatsOf(explained);

        // No index covers a case-insensitive infix regex on "name", so every document in
        // the collection is examined regardless of how the planner labels the stage.
        assertThat(plan.toJson()).contains("COLLSCAN");
        assertThat(stats.getInteger("totalDocsExamined")).isGreaterThanOrEqualTo(FILLER_SPACES);
    }

    @Test
    @DisplayName("the public search API returns the same single match the plan proves was examined")
    void searchReturnsTheMatch() {
        SpaceSearch.Result result = spaceSearch.search(new SpaceSearch.Filters("302", null, null, null, null, null), 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).extracting(SpaceDocument::code).containsExactly("302");
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    /** Runs MongoDB's own {@code explain} over a query built exactly as the service builds it. */
    private Document explain(Query query) {
        Document find = new Document("find", "spaces").append("filter", query.getQueryObject());

        if (!query.getSortObject().isEmpty()) {
            find.append("sort", query.getSortObject());
        }
        if (query.getSkip() > 0) {
            find.append("skip", query.getSkip());
        }
        if (query.getLimit() > 0) {
            find.append("limit", query.getLimit());
        }

        return mongoTemplate.getDb().runCommand(
                new Document("explain", find).append("verbosity", "executionStats"));
    }

    private static Document winningPlanOf(Document explained) {
        return explained.get("queryPlanner", Document.class).get("winningPlan", Document.class);
    }

    private static Document executionStatsOf(Document explained) {
        return explained.get("executionStats", Document.class);
    }
}

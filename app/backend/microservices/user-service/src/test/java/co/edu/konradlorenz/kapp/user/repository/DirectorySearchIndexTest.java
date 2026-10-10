package co.edu.konradlorenz.kapp.user.repository;

import co.edu.konradlorenz.kapp.user.AbstractUserServiceTest;
import co.edu.konradlorenz.kapp.user.domain.SearchTokens;
import co.edu.konradlorenz.kapp.user.domain.UserProfile;
import co.edu.konradlorenz.kapp.user.domain.UserRole;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves, rather than asserts, that the accent-insensitive search is served from an index.
 *
 * <p>"It uses the index" is the kind of claim that is true when it is written and quietly
 * false a year later, because nothing fails when it stops being true: the results stay
 * correct and only the latency changes. So these tests hand MongoDB's own {@code explain}
 * the exact query the service builds and read the plan it chose.
 *
 * <p>The last test is the control. It runs the naive implementation - the case-insensitive
 * regex someone will eventually propose as "simpler" - against the same data and shows it
 * scanning the whole collection and finding nothing.
 */
class DirectorySearchIndexTest extends AbstractUserServiceTest {

    private static final int SEEDED_PROFILES = 60;

    @BeforeEach
    void seedDirectory() {
        // Two profiles the searches below are about, and enough filler that a collection
        // scan is clearly distinguishable from an index hit in the execution stats.
        save(student("11111111-0000-0000-0000-000000000001",
                "laura.munoz@konradlorenz.edu.co", "Laura Sofía", "Muñoz Peña"));
        save(student("11111111-0000-0000-0000-000000000002",
                "pepito.perez@konradlorenz.edu.co", "Pepito", "Perez Gomez"));

        for (int i = 3; i <= SEEDED_PROFILES; i++) {
            save(student("11111111-0000-0000-0000-%012d".formatted(i),
                    "filler%d@konradlorenz.edu.co".formatted(i),
                    "Filler%d".formatted(i), "Placeholder%d".formatted(i)));
        }
    }

    // ---------------------------------------------------------------------------------
    // The plan MongoDB actually chooses
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("the filter the service builds is answered by an index scan")
    void filterIsAnsweredByAnIndexScan() {
        Query query = new Query(UserDirectoryQueries.filter(null, null, List.of("munoz")));

        Document plan = winningPlanOf(explain(query));

        assertThat(plan.toJson()).contains("IXSCAN").contains("ix_users_search_tokens");
    }

    @Test
    @DisplayName("the index scan reads only the matching documents, not the collection")
    void indexScanExaminesOnlyWhatMatches() {
        Query query = new Query(UserDirectoryQueries.filter(null, null, List.of("munoz")));

        Document stats = executionStatsOf(explain(query));

        // One profile carries the token. Anything approaching 60 would mean the regex was
        // being applied to every document rather than used as an index bound.
        assertThat(stats.getInteger("nReturned")).isEqualTo(1);
        assertThat(stats.getInteger("totalDocsExamined")).isEqualTo(1);
    }

    @Test
    @DisplayName("the full paged query keeps the index scan once sorting and paging are added")
    void pagedQueryStillUsesTheIndex() {
        Query query = UserDirectoryQueries.page(null, null, List.of("munoz"), 0, 20);

        Document plan = winningPlanOf(explain(query));

        assertThat(plan.toJson()).contains("IXSCAN").contains("ix_users_search_tokens");
    }

    @Test
    @DisplayName("the regex sent to MongoDB is anchored and carries no options")
    void regexIsAnchoredAndFlagless() {
        Document filter = new Query(UserDirectoryQueries.filter(null, null, List.of("munoz")))
                .getQueryObject();

        // Serialised the way the driver will send it: /^munoz/ with an empty options
        // field. An "i" in there would cost the index; a missing "^" would too.
        assertThat(filter.toJson())
                .contains("\"pattern\": \"^munoz\"")
                .contains("\"options\": \"\"");
    }

    @Test
    @DisplayName("a term that looks like a regex is escaped instead of being executed")
    void regexMetacharactersAreEscaped() {
        assertThat(UserDirectoryQueries.prefixRegex("a.c")).isEqualTo("^a\\.c");
        assertThat(UserDirectoryQueries.prefixRegex("x*")).isEqualTo("^x\\*");
        assertThat(UserDirectoryQueries.prefixRegex("pepito.perez@konradlorenz.edu.co"))
                .isEqualTo("^pepito\\.perez@konradlorenz\\.edu\\.co");
    }

    @Test
    @DisplayName("the naive case-insensitive regex examines every document and still finds nothing")
    void theNaiveAlternativeScansEverythingAndFindsNothing() {
        // The implementation this design exists to avoid: match the raw text with an "i"
        // flag instead of folding both sides.
        Query naive = new Query(Criteria.where("searchTokens").regex("muñoz", "i"));

        Document explained = explain(naive);
        Document plan = winningPlanOf(explained);
        Document stats = executionStatsOf(explained);

        // Whether the planner labels this stage IXSCAN or COLLSCAN is a version and
        // heuristic detail - on this index shape MongoDB 7 still reports IXSCAN, but over
        // the degenerate, unbounded range ["MinKey", "MaxKey") rather than a prefix bound,
        // because an unanchored, case-insensitive regex carries no usable start or end.
        // Whichever label it picks, the bound is not narrowed, so every document is still
        // examined - the whole reason the anchored, flagless form exists.
        assertThat(plan.toJson()).doesNotContain("\"^munoz\"");
        assertThat(stats.getInteger("totalDocsExamined")).isEqualTo(SEEDED_PROFILES);
        // Wrong twice over: it read every document and matched none of them, because the
        // stored token is "munoz" and no case flag will bridge the tilde.
        assertThat(stats.getInteger("nReturned")).isZero();
    }

    // ---------------------------------------------------------------------------------
    // What the plan means for the caller
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("an unaccented query finds the accented name")
    void unaccentedQueryFindsAccentedName() {
        assertThat(searchFor("munoz")).extracting(UserProfile::lastName).containsExactly("Muñoz Peña");
    }

    @Test
    @DisplayName("an accented query finds the same profile")
    void accentedQueryFindsTheSameProfile() {
        assertThat(searchFor("MUÑOZ")).extracting(UserProfile::lastName).containsExactly("Muñoz Peña");
    }

    @Test
    @DisplayName("a word prefix matches, which is what a type-ahead needs")
    void wordPrefixMatches() {
        assertThat(searchFor("per")).extracting(UserProfile::lastName)
                .containsExactly("Perez Gomez");
    }

    @Test
    @DisplayName("an infix does not match, and the mobile team is told so")
    void infixDoesNotMatch() {
        // "ere" is inside "perez" but does not begin it. Correct and deliberate: an infix
        // search cannot be served from an index at any size, so the interface must not
        // offer one.
        assertThat(searchFor("ere")).isEmpty();
    }

    @Test
    @DisplayName("every word of the query must match, so two terms narrow the result")
    void everyTermMustMatch() {
        assertThat(searchFor("pepito perez")).hasSize(1);
        assertThat(searchFor("pepito munoz")).isEmpty();
    }

    @Test
    @DisplayName("a search on the e-mail finds the account")
    void searchesTheEmailToo() {
        assertThat(searchFor("pepito.perez@konradlorenz.edu.co")).hasSize(1);
    }

    @Test
    @DisplayName("filters combine with the search rather than replacing it")
    void filtersCombineWithTheSearch() {
        save(professor("22222222-0000-0000-0000-000000000001",
                "laura.munoz.docente@konradlorenz.edu.co", "Laura", "Muñoz"));

        List<UserProfile> professors = new UserDirectoryRepository(mongoTemplate)
                .findPage(UserRole.ROLE_PROFESSOR, null, SearchTokens.forQuery("munoz"), 0, 20);

        assertThat(professors).extracting(UserProfile::email)
                .containsExactly("laura.munoz.docente@konradlorenz.edu.co");
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private List<UserProfile> searchFor(String query) {
        return new UserDirectoryRepository(mongoTemplate)
                .findPage(null, null, SearchTokens.forQuery(query), 0, 20);
    }

    /** Runs MongoDB's {@code explain} over a query built exactly as the service builds it. */
    private Document explain(Query query) {
        Document find = new Document("find", "users").append("filter", query.getQueryObject());

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

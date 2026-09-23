package co.edu.konradlorenz.kapp.map.service;

import co.edu.konradlorenz.kapp.map.domain.SpaceDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The campus-wide space search.
 *
 * <h2>Why this is a text index and not a regex</h2>
 * The search has to be case- AND accent-insensitive: a student types "matematicas" and
 * expects "Matemáticas", and types "sala de sistemas" for a room whose door says
 * "Laboratorio de Sistemas". The obvious implementation - a case-insensitive regex - fails
 * both halves at once: {@code /matematicas/i} does not match "Matemáticas" at all, because
 * case folding is not accent folding, and a regex carrying the {@code i} flag cannot be
 * served from an index, so it degrades to a collection scan as the map grows.
 *
 * <p>MongoDB's text index solves both properly. Terms are folded to lower case AND stripped
 * of diacritics when the index is built and again when the query is parsed, so the
 * insensitivity is a property of the index rather than something this code re-implements.
 * {@code V004_MapModelV2} declares one text index over {@code doorCode}, {@code name} and
 * {@code aliases} with Spanish as the default language, so Spanish stemming and stop words
 * apply: "de" and "la" are dropped, and "salones" and "salon" reach each other.
 *
 * <p>The index covers the door code, not the internal {@code code}. For a numbered room they
 * are the same; for a dependency with nothing on its door the internal code is generated, and a
 * search that matched it would put an invented identifier in front of the person searching.
 *
 * <h2>Ranking</h2>
 * The index weights {@code doorCode} 5, {@code name} 3 and {@code aliases} 2, and results are
 * sorted by {@code $meta: "textScore"} descending, then by {@code code} ascending so that
 * equal scores come back in a stable order rather than in whatever order the storage engine
 * felt like. The weights are what make a search for "708" return room 708 itself ahead of
 * a room that merely mentions 708 in an alias.
 *
 * <h2>Consequence worth telling the mobile team</h2>
 * A text index matches WORDS, not prefixes: "sistemas" finds the lab, "sistem" does not.
 * This is a search box the student submits, not a type-ahead that fires on every keystroke.
 * Prefix matching would need a second, differently-shaped index, and MongoDB permits only
 * one text index per collection.
 */
@Component
public class SpaceSearch {

    /**
     * Must match the {@code default_language} of {@code tx_spaces_search}. Passing it
     * explicitly keeps the query's stemmer and stop-word list identical to the one the
     * index was built with, instead of relying on the index default staying put.
     */
    private static final String LANGUAGE = "spanish";

    private final MongoTemplate mongo;

    public SpaceSearch(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * @param content       the requested page, most relevant first
     * @param totalElements matches across every page
     */
    public record Result(List<SpaceDocument> content, long totalElements) {
    }

    /**
     * @param typeCodes null for any type; otherwise the space's type has to be one of these. A
     *                  category filter arrives here already resolved to its types
     * @param wing      a wing code of the building
     * @param floorCode a floor code, meaningful together with a building
     */
    public record Filters(String q, String campus, List<String> typeCodes, String buildingCode,
                          String wing, String floorCode) {
    }

    public Result search(Filters filters, int page, int size) {

        boolean searching = StringUtils.hasText(filters.q());
        String term = sanitize(filters.q());

        if (searching && term.isEmpty()) {
            // Everything the caller typed was punctuation the text query language would
            // have read as an operator. An empty $search is an error in MongoDB, and an
            // empty page is the honest answer.
            return new Result(List.of(), 0);
        }

        if (!searching) {
            // No term at all is a different question: "show me what is in this building",
            // which is how somebody manages a floor rather than how a student finds a room.
            // It cannot go through the text index - $search has nothing to match - so it is
            // an ordinary filtered query, ordered the way a person reads a building: by
            // floor, then by code.
            return browse(filters, page, size);
        }

        TextQuery query = buildFilter(term, filters);

        // Counted before skip and limit are set: MongoTemplate folds both into the count's
        // options, so counting afterwards would return at most one page's worth.
        long total = mongo.count(query, SpaceDocument.class);

        query.sortByScore();
        query.with(Sort.by(Sort.Direction.ASC, "code"));
        query.skip((long) page * size).limit(size);

        return new Result(mongo.find(query, SpaceDocument.class), total);
    }

    /**
     * Listing rather than searching: every space matching the filters, in reading order.
     *
     * <p>Unbounded in principle and bounded in practice by the same page size the search uses.
     * There is no text index involved and none is wanted: {@code $search} needs something to
     * match, and "everything in building A" has nothing to match on.
     */
    private Result browse(Filters filters, int page, int size) {
        Query query = new Query();
        addFilters(query, filters);

        long total = mongo.count(query, SpaceDocument.class);

        query.with(Sort.by(Sort.Direction.ASC, "buildingCode", "floorLevel", "code"));
        query.skip((long) page * size).limit(size);

        return new Result(mongo.find(query, SpaceDocument.class), total);
    }

    /**
     * The one place the search filter is assembled, shared by {@link #search} and by
     * {@code SpaceSearchIndexTest}, which hands this exact filter to MongoDB's
     * {@code explain} to prove it is served from {@code tx_spaces_search} rather than a
     * collection scan. A second, hand-copied query in the test would only prove that the
     * test's own idea of the filter uses the index, not that the service's does.
     *
     * @param term already sanitized by {@link #sanitize(String)}; not the raw {@code q}
     */
    static TextQuery buildFilter(String term, Filters filters) {
        TextQuery query = new TextQuery(TextCriteria.forLanguage(LANGUAGE).matching(term));
        addFilters(query, filters);
        return query;
    }

    private static void addFilters(Query query, Filters filters) {
        if (StringUtils.hasText(filters.campus())) {
            query.addCriteria(Criteria.where("campus").is(filters.campus()));
        }
        if (filters.typeCodes() != null) {
            query.addCriteria(Criteria.where("typeCode").in(filters.typeCodes()));
        }
        if (StringUtils.hasText(filters.buildingCode())) {
            query.addCriteria(Criteria.where("buildingCode").is(filters.buildingCode()));
        }
        if (StringUtils.hasText(filters.wing())) {
            // The wing is a stored field, not a suffix parsed out of the code at query time:
            // Bienestar's rooms carry no wing mark at all.
            query.addCriteria(Criteria.where("wing").is(filters.wing()));
        }
        if (StringUtils.hasText(filters.floorCode())) {
            query.addCriteria(Criteria.where("floorCode").is(filters.floorCode()));
        }
    }

    /**
     * Strips the characters that mean something to MongoDB's text query language, so a
     * student's search box cannot accidentally drive it.
     *
     * <p>{@code $search} is a small language, not a literal: {@code "a phrase"} forces an
     * exact phrase and a leading {@code -} EXCLUDES a term. Someone searching for
     * {@code -302} would otherwise get every room except the one they were looking for, and
     * an unbalanced quote turns the rest of the query into a phrase. Neither is a security
     * hole - the operators only ever narrow a read of public data - but both are baffling
     * from the far side of a search box.
     */
    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String withoutOperators = raw.replace('"', ' ').replace('\\', ' ');
        return Arrays.stream(withoutOperators.trim().split("\\s+"))
                .map(word -> word.replaceAll("^-+", ""))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.joining(" "));
    }
}

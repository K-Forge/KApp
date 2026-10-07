package co.edu.konradlorenz.kapp.user.repository;

import co.edu.konradlorenz.kapp.user.domain.SearchTokens;
import co.edu.konradlorenz.kapp.user.domain.UserRole;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the MongoDB query behind {@code GET /api/users}.
 *
 * <p>Separated from the repository that runs it so that a test can assert on the exact
 * filter the service sends, and hand that same filter to MongoDB's {@code explain}. An
 * index that "should" be used is worth nothing; this makes the claim checkable.
 */
public final class UserDirectoryQueries {

    /** Characters that mean something to a regex engine and nothing to a name. */
    private static final String REGEX_METACHARACTERS = "\\^$.|?*+()[]{}";

    /** Newest account first, with the id as a tie-break so paging is stable. */
    public static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id"));

    /**
     * The filter alone, without paging or sorting. Every supplied filter narrows the same
     * result set, combined with AND.
     *
     * @param role   restrict to accounts holding this role among others, or null for every role
     * @param active restrict to active or deactivated accounts, or null for both
     * @param terms  folded search terms from {@link SearchTokens#forQuery(String)}; an
     *               empty list means no text filter, so callers that folded a non-empty
     *               query down to nothing must not reach here
     */
    public static Criteria filter(UserRole role, Boolean active, List<String> terms) {
        List<Criteria> clauses = new ArrayList<>();

        if (role != null) {
            // roles is a list: equality matches a document that holds the role among others.
            clauses.add(Criteria.where("roles").is(role));
        }
        if (active != null) {
            clauses.add(Criteria.where("active").is(active));
        }
        for (String term : terms) {
            clauses.add(Criteria.where("searchTokens").regex(prefixRegex(term)));
        }

        // A single clause is emitted bare rather than wrapped in a one-element $and.
        // Equivalent to the planner, but it keeps the filter - and so the explain output
        // a test reads - as simple as the query actually is.
        return switch (clauses.size()) {
            case 0 -> new Criteria();
            case 1 -> clauses.get(0);
            default -> new Criteria().andOperator(clauses);
        };
    }

    /** The full query the service runs: filter, newest first, one page. */
    public static Query page(UserRole role, Boolean active, List<String> terms,
                             int page, int size) {
        return new Query(filter(role, active, terms))
                .with(NEWEST_FIRST)
                .skip((long) page * size)
                .limit(size);
    }

    /**
     * Turns one folded term into the only kind of regex MongoDB can serve from an index:
     * anchored at the start, and with no options.
     *
     * <p>The anchor is what lets the planner convert the pattern into a bounded scan of
     * index keys beginning with the term. The absent {@code i} flag is the other half: a
     * case-insensitive regex has no usable bounds, because the keys are ordered by their
     * exact bytes. Case and accents were already removed on both sides when the text was
     * folded, so the flag would buy nothing and cost the index.
     *
     * <p>Returned as a {@link String} rather than a compiled {@link java.util.regex.Pattern}
     * so that no Java flag can leak into the BSON options field by accident.
     */
    static String prefixRegex(String foldedTerm) {
        StringBuilder regex = new StringBuilder(foldedTerm.length() + 4).append('^');
        for (int i = 0; i < foldedTerm.length(); i++) {
            char c = foldedTerm.charAt(i);
            if (REGEX_METACHARACTERS.indexOf(c) >= 0) {
                regex.append('\\');
            }
            regex.append(c);
        }
        return regex.toString();
    }

    private UserDirectoryQueries() {
    }
}

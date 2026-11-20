package co.edu.konradlorenz.kapp.auth.service;

import co.edu.konradlorenz.kapp.auth.domain.InvitationCode;
import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.auth.web.InvitationCodeRequest;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.common.error.DuplicateResourceException;
import co.edu.konradlorenz.kapp.common.error.ResourceNotFoundException;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Claims and returns uses of an invitation code.
 *
 * <p><strong>Never read-then-write.</strong> A registration that loads a code, checks
 * {@code timesUsed < maxUses} in Java and saves the increment back will hand out the last
 * use of a code twice whenever two requests interleave, and it will do so silently and
 * only under load. Both operations here are a single guarded {@code findAndModify}, so the
 * check and the change happen together on the server.
 */
@Service
public class InvitationCodeService {

    /** No O/0 or I/1: the pairs people misread when a code is read out loud. */

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int CODE_GROUP = 4;

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();


    private static final Logger log = LoggerFactory.getLogger(InvitationCodeService.class);

    private final MongoTemplate mongo;

    public InvitationCodeService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * Atomically claims one use of a code.
     *
     * @return the code as it stands after the claim
     * @throws BusinessRuleException when the code is unknown, inactive, expired or spent.
     *         All four are one message on purpose: telling a registrant that a code exists
     *         but is spent is more than they need to know.
     */
    public InvitationCode redeem(String rawCode) {
        String code = normalise(rawCode);
        Date now = Date.from(Instant.now());

        // Raw filter rather than Criteria: $expr compares two fields of the same document,
        // which is the whole point - the cap travels with the code instead of being read
        // into the application first.
        Document filter = new Document("code", code)
                .append("active", true)
                .append("$or", List.of(
                        new Document("expiresAt", null),
                        new Document("expiresAt", new Document("$gt", now))))
                .append("$expr", new Document("$lt", List.of("$timesUsed", "$maxUses")));

        Update update = new Update().inc("timesUsed", 1).set("updatedAt", now);

        InvitationCode redeemed = mongo.findAndModify(
                new BasicQuery(filter), update,
                FindAndModifyOptions.options().returnNew(true),
                InvitationCode.class, InvitationCode.COLLECTION);

        if (redeemed == null) {
            log.info("Invitation code refused: {}", code);
            throw new BusinessRuleException("Invitation code is invalid, expired or already used",
                    List.of(new ApiError.FieldIssue("invitationCode",
                            "Unknown, expired or already used")));
        }

        log.info("Invitation code {} redeemed, {} of {} uses left",
                code, redeemed.remainingUses(), redeemed.maxUses());
        return redeemed;
    }

    /**
     * Returns a use claimed by a registration that then failed.
     *
     * <p>Guarded by {@code $expr: {$gt: ["$timesUsed", 0]}} so a double release - a retry,
     * a compensating path that runs twice - cannot drive the counter below zero and hand
     * out more accounts than the registrar authorised.
     */
    public void release(String rawCode) {
        String code = normalise(rawCode);
        Document filter = new Document("code", code)
                .append("$expr", new Document("$gt", List.of("$timesUsed", 0)));

        Update update = new Update().inc("timesUsed", -1).set("updatedAt", Date.from(Instant.now()));

        InvitationCode released = mongo.findAndModify(
                new BasicQuery(filter), update,
                FindAndModifyOptions.options().returnNew(true),
                InvitationCode.class, InvitationCode.COLLECTION);

        if (released == null) {
            log.warn("Could not release invitation code {}: unknown or already at zero", code);
        } else {
            log.info("Released invitation code {}, {} uses left", code, released.remainingUses());
        }
    }

    /**
     * The roles a code may grant.
     *
     * <p>{@code ROLE_ADMIN} is absent and must stay absent. Codes are handed out on paper
     * and at a counter, and two of them ship in this repository; a code that granted admin
     * would mean anyone who can read the repo can escalate. Administrators are promoted
     * deliberately, by someone who already is one.
     */
    private static final Set<String> GRANTABLE_ROLES = Set.of("ROLE_STUDENT", "ROLE_PROFESSOR", "ROLE_STAFF");

    /**
     * @param redeemable when true, only codes that could be redeemed right now: active,
     *                   unexpired and with uses left. When false, only the codes that
     *                   could not. Null returns every code.
     */
    public List<InvitationCode> list(Boolean redeemable) {
        List<InvitationCode> all = mongo.find(
                new BasicQuery(new Document()).with(Sort.by(Sort.Direction.ASC, "code")),
                InvitationCode.class, InvitationCode.COLLECTION);
        if (redeemable == null) {
            return all;
        }
        Instant now = Instant.now();
        return all.stream().filter(c -> isRedeemable(c, now) == redeemable).toList();
    }

    /**
     * Redeemability as {@link #redeem} defines it, evaluated in Java for the listing only.
     *
     * <p>The filter is not pushed into the query on purpose: the authoritative rule lives
     * in {@code redeem}'s {@code $expr} guard, and expressing it a second time as Mongo
     * criteria would give two definitions of "usable" that can drift apart. There are tens
     * of codes, not millions.
     */
    private static boolean isRedeemable(InvitationCode c, Instant now) {
        return c.active()
                && (c.expiresAt() == null || c.expiresAt().isAfter(now))
                && c.timesUsed() < c.maxUses();
    }

    /**
     * @throws BusinessRuleException      (400) if the role is not one a code may grant
     * @throws DuplicateResourceException (409) if the code already exists. Reusing a value
     *                                    would make "which one did I just decrement"
     *                                    unanswerable, which is what the unique index on
     *                                    {@code code} exists to prevent
     */
    /**
     * A unique code, minted here rather than typed by a person.
     *
     * <p>Grouped as {@code KL-7K2M-QX9P} - four characters at a time is what somebody reads back
     * over a counter without asking twice. The alphabet leaves out O, 0, I and 1, which are the
     * pairs people actually confuse; 32^8 is about a thousand billion values, so the codes that
     * ship in a public repository stop being a list anybody can extend by guessing.
     *
     * <p>The uniqueness check is still here even at that size: the cost of a duplicate is one
     * intake sharing another intake's quota, and ten collisions in a row would mean the random
     * source is broken rather than that we were unlucky.
     */
    private String nextCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder("KL-");
            for (int i = 0; i < CODE_GROUP * 2; i++) {
                if (i == CODE_GROUP) {
                    code.append('-');
                }
                code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String candidate = code.toString();
            if (find(candidate) == null) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate an unused invitation code");
    }

    public InvitationCode create(InvitationCodeRequest request) {
        if (!GRANTABLE_ROLES.contains(request.role())) {
            throw new BusinessRuleException(
                    "An invitation code may only grant a profile role",
                    List.of(new ApiError.FieldIssue("role",
                            "must be one of ROLE_STUDENT, ROLE_PROFESSOR, ROLE_STAFF")));
        }
        String code = nextCode();

        Instant now = Instant.now();
        InvitationCode created = new InvitationCode(
                null, code, request.role(), request.maxUses(), 0, true,
                request.expiresAt(), request.notes(), now, now);

        InvitationCode saved = mongo.insert(created, InvitationCode.COLLECTION);
        log.info("Invitation code {} created for {} with {} uses", code, saved.role(), saved.maxUses());
        return saved;
    }

    /**
     * Flips the off switch. Deactivating is the reversible way to stop a code being
     * redeemed and it keeps the record of who used it, which is why it exists alongside
     * deletion.
     *
     * <p>A targeted {@code $set} rather than saving the whole document: a full save would
     * write {@code timesUsed} back from a value read earlier and could undo a redemption
     * that landed in between.
     *
     * @throws ResourceNotFoundException (404) if no such code exists
     */
    public InvitationCode setActive(String rawCode, boolean active) {
        String code = normalise(rawCode);
        Update update = new Update().set("active", active).set("updatedAt", Date.from(Instant.now()));

        InvitationCode updated = mongo.findAndModify(
                new BasicQuery(new Document("code", code)), update,
                FindAndModifyOptions.options().returnNew(true),
                InvitationCode.class, InvitationCode.COLLECTION);

        if (updated == null) {
            throw new ResourceNotFoundException("Invitation code", code);
        }
        log.info("Invitation code {} set active={}", code, active);
        return updated;
    }

    /**
     * @throws ResourceNotFoundException (404) if no such code exists
     */
    public void delete(String rawCode) {
        String code = normalise(rawCode);
        InvitationCode existing = find(code);
        if (existing == null) {
            throw new ResourceNotFoundException("Invitation code", code);
        }
        mongo.remove(new BasicQuery(new Document("code", code)),
                InvitationCode.class, InvitationCode.COLLECTION);

        // Deleting a code that has already been redeemed discards the only record of how
        // those accounts came to exist. Deactivating keeps it; the contract says to prefer
        // that, and this line is what makes the choice visible when someone did not.
        if (existing.timesUsed() > 0) {
            log.warn("Invitation code {} deleted after {} redemption(s); that record is now gone",
                    code, existing.timesUsed());
        } else {
            log.info("Invitation code {} deleted, never redeemed", code);
        }
    }

    /** Read-only lookup, for diagnostics and tests. Never the basis of a redemption. */
    public InvitationCode find(String rawCode) {
        Query query = new BasicQuery(new Document("code", normalise(rawCode)));
        return mongo.findOne(query, InvitationCode.class, InvitationCode.COLLECTION);
    }

    private static String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}

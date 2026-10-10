package co.edu.konradlorenz.kapp.user.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.util.List;

/**
 * Brings every stored profile to user 1.0: KApp keeps no identity document, no phone number and no
 * student code, and a profile holds a list of roles rather than one.
 *
 * <ul>
 *   <li>{@code identification}, {@code phone} and {@code academic} - the student code and the
 *       program, pensum and level copied at registration - are removed. A student's program, pensum
 *       and level are read from SINU now, for the student, and never stored.</li>
 *   <li>{@code role} becomes {@code roles}, a list holding it.</li>
 *   <li>The index on {@code academic.studentCode} that {@code V001} made goes with the field, and
 *       {@code roles} is indexed for the directory's role filter.</li>
 * </ul>
 *
 * <p>Works on the raw documents, because the domain no longer has these fields. Running it twice does
 * nothing the second time. Change units are append-only: never edit one that has run; add a new one.
 */
@ChangeUnit(id = "user-profiles-without-documents-v003", order = "003", author = "kapp")
public class V003_ProfilesWithoutDocuments {

    private static final Logger log = LoggerFactory.getLogger(V003_ProfilesWithoutDocuments.class);

    static final String COLLECTION = "users";
    static final String STUDENT_CODE_INDEX = "ix_users_student_code";
    static final String ROLES_INDEX = "ix_users_roles";

    @Execution
    public void execute(MongoTemplate mongo) {
        long rewritten = 0;
        for (Document profile : mongo.getCollection(COLLECTION).find(new Document("$or", List.of(
                new Document("role", new Document("$exists", true)),
                new Document("identification", new Document("$exists", true)),
                new Document("phone", new Document("$exists", true)),
                new Document("academic", new Document("$exists", true)))))) {
            Document set = new Document();
            Object role = profile.get("role");
            if (role != null && !profile.containsKey("roles")) {
                set.append("roles", List.of(role));
            }
            Document update = new Document("$unset", new Document("role", "")
                    .append("identification", "").append("phone", "").append("academic", ""));
            if (!set.isEmpty()) {
                update.append("$set", set);
            }
            mongo.getCollection(COLLECTION).updateOne(new Document("_id", profile.get("_id")), update);
            rewritten++;
        }

        if (hasIndex(mongo, STUDENT_CODE_INDEX)) {
            mongo.indexOps(COLLECTION).dropIndex(STUDENT_CODE_INDEX);
        }
        mongo.indexOps(COLLECTION).createIndex(new Index().on("roles", Sort.Direction.ASC).named(ROLES_INDEX));
        log.info("{} profiles brought to user 1.0: no document, phone or academic copy, and a list of roles",
                rewritten);
    }

    /**
     * Gives each profile back a single {@code role}, the first of its roles. What was removed cannot
     * come back: KApp no longer keeps it anywhere.
     */
    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        for (Document profile : mongo.getCollection(COLLECTION).find(new Document("roles", new Document("$exists", true)))) {
            List<?> roles = profile.getList("roles", Object.class, List.of());
            Document update = new Document("$unset", new Document("roles", ""));
            if (!roles.isEmpty()) {
                update.append("$set", new Document("role", roles.get(0)));
            }
            mongo.getCollection(COLLECTION).updateOne(new Document("_id", profile.get("_id")), update);
        }
        if (hasIndex(mongo, ROLES_INDEX)) {
            mongo.indexOps(COLLECTION).dropIndex(ROLES_INDEX);
        }
    }

    private static boolean hasIndex(MongoTemplate mongo, String name) {
        return mongo.indexOps(COLLECTION).getIndexInfo().stream().map(IndexInfo::getName).anyMatch(name::equals);
    }
}

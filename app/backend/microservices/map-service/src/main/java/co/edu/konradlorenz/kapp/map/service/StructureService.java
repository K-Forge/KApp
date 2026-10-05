package co.edu.konradlorenz.kapp.map.service;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.map.domain.CampusStructuresDocument;
import co.edu.konradlorenz.kapp.map.domain.CampusStructuresRepository;
import co.edu.konradlorenz.kapp.map.domain.Structure;
import co.edu.konradlorenz.kapp.map.web.dto.StructureDto;
import co.edu.konradlorenz.kapp.map.web.dto.StructuresRequest;
import co.edu.konradlorenz.kapp.map.web.dto.StructuresResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What stands on a campus's blocks besides the university: read by anybody who reads the map, and
 * replaced whole by the portal while a block is surveyed.
 *
 * <p>A save carries the version it read. If somebody saved the list since, it is refused rather
 * than laid over theirs, as a floor's layout is.
 */
@Service
public class StructureService {

    private static final Logger log = LoggerFactory.getLogger(StructureService.class);

    private final CampusStructuresRepository repository;
    private final MongoTemplate mongo;

    public StructureService(CampusStructuresRepository repository, MongoTemplate mongo) {
        this.repository = repository;
        this.mongo = mongo;
    }

    /** The campus's structures; none, at version 0, while nothing has been saved for it. */
    public StructuresResponse forCampus(String campus) {
        return repository.findById(GroundService.key(campus))
                .map(StructureService::toResponse)
                .orElse(new StructuresResponse(campus.trim(), List.of(), 0, null));
    }

    public StructuresResponse replace(String campus, StructuresRequest request) {
        List<ApiError.FieldIssue> issues = new ArrayList<>();
        for (int i = 0; i < request.structures().size(); i++) {
            if (!BuildingService.closedRingOnEarth(request.structures().get(i).ring())) {
                issues.add(new ApiError.FieldIssue("structures[" + i + "].ring",
                        "An outline is [lon, lat] points on the earth, the first repeated at the end"));
            }
        }
        if (!issues.isEmpty()) {
            throw new BusinessRuleException("The structures' outlines are not closed rings on the earth", issues);
        }

        String id = GroundService.key(campus);
        Optional<CampusStructuresDocument> current = repository.findById(id);
        long stored = current.map(CampusStructuresDocument::version).orElse(0L);
        if (stored != request.version()) {
            throw stale(stored, request.version());
        }
        List<Structure> structures = request.structures().stream().map(StructureService::toStructure).toList();
        CampusStructuresDocument next = new CampusStructuresDocument(id,
                current.map(CampusStructuresDocument::campus).orElse(campus.trim()), structures, stored + 1,
                Instant.now());
        write(current.isPresent(), next, stored);
        log.info("Saved {} structure(s) of campus {} at version {}", structures.size(), next.campus(), next.version());
        return toResponse(next);
    }

    /** Written only while the stored list still has the version that was checked. */
    private void write(boolean exists, CampusStructuresDocument next, long checked) {
        if (!exists) {
            try {
                mongo.insert(next);
            } catch (DuplicateKeyException raced) {
                throw stale(1, checked);
            }
            return;
        }
        Query query = Query.query(Criteria.where("_id").is(next.id()).and("version").is(checked));
        Update update = new Update()
                .set("structures", next.structures())
                .set("version", next.version())
                .set("updatedAt", next.updatedAt());
        if (mongo.updateFirst(query, update, CampusStructuresDocument.class).getModifiedCount() == 0) {
            throw stale(checked + 1, checked);
        }
    }

    private static MapConflictException stale(long stored, long sent) {
        return new MapConflictException(
                "The campus's structures were saved by someone else since you opened them. Reload to see their changes.",
                List.of(new ApiError.FieldIssue("version", "you sent %d, the list is at %d".formatted(sent, stored))));
    }

    private static Structure toStructure(StructureDto dto) {
        return new Structure(dto.name().trim(), dto.floors(), dto.basements(),
                dto.lot() == null || dto.lot().isBlank() ? null : dto.lot().trim(), dto.ring());
    }

    private static StructuresResponse toResponse(CampusStructuresDocument document) {
        return new StructuresResponse(document.campus(),
                document.structures().stream()
                        .map(s -> new StructureDto(s.name(), s.floors(), s.basements(), s.lot(), s.ring()))
                        .toList(),
                document.version(), document.updatedAt());
    }
}

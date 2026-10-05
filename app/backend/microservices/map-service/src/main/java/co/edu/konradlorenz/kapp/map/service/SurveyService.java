package co.edu.konradlorenz.kapp.map.service;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.map.domain.CampusSurveyDocument;
import co.edu.konradlorenz.kapp.map.domain.CampusSurveyRepository;
import co.edu.konradlorenz.kapp.map.domain.SurveyMeasure;
import co.edu.konradlorenz.kapp.map.web.dto.SurveyMeasureDto;
import co.edu.konradlorenz.kapp.map.web.dto.SurveyRequest;
import co.edu.konradlorenz.kapp.map.web.dto.SurveyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The distances taken on site round a campus's blocks, which the portal's survey sheet saves as
 * they are typed in: where each wall stands from the curb, and how long it is. They are what the
 * buildings' outlines are then drawn from.
 *
 * <p>Saved whole with the version they were read at, as the structures are. A distance keeps the
 * time it was taken until it changes, so the survey tells what was measured when.
 */
@Service
public class SurveyService {

    private static final Logger log = LoggerFactory.getLogger(SurveyService.class);

    private final CampusSurveyRepository repository;
    private final MongoTemplate mongo;

    public SurveyService(CampusSurveyRepository repository, MongoTemplate mongo) {
        this.repository = repository;
        this.mongo = mongo;
    }

    /** The campus's survey; nothing taken, at version 0, while nothing has been saved for it. */
    public SurveyResponse forCampus(String campus) {
        return repository.findById(GroundService.key(campus))
                .map(SurveyService::toResponse)
                .orElse(new SurveyResponse(campus.trim(), List.of(), 0, null));
    }

    public SurveyResponse replace(String campus, SurveyRequest request) {
        List<ApiError.FieldIssue> issues = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < request.measures().size(); i++) {
            if (!seen.add(request.measures().get(i).id())) {
                issues.add(new ApiError.FieldIssue("measures[" + i + "].id", "Another distance has this id"));
            }
        }
        if (!issues.isEmpty()) {
            throw new BusinessRuleException("Two distances of the survey have the same id", issues);
        }

        String id = GroundService.key(campus);
        Optional<CampusSurveyDocument> current = repository.findById(id);
        long stored = current.map(CampusSurveyDocument::version).orElse(0L);
        if (stored != request.version()) {
            throw stale(stored, request.version());
        }
        // To the millisecond, as the database keeps it: the time a save answers with is the one a
        // later read gives back.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Map<String, SurveyMeasure> before = current.map(CampusSurveyDocument::measures).orElse(List.of()).stream()
                .collect(Collectors.toMap(SurveyMeasure::id, Function.identity()));
        List<SurveyMeasure> measures = request.measures().stream()
                .map(dto -> toMeasure(dto, before.get(dto.id()), now))
                .toList();
        CampusSurveyDocument next = new CampusSurveyDocument(id,
                current.map(CampusSurveyDocument::campus).orElse(campus.trim()), measures, stored + 1, now);
        write(current.isPresent(), next, stored);
        log.info("Saved {} distance(s) of campus {}'s survey at version {}", measures.size(), next.campus(),
                next.version());
        return toResponse(next);
    }

    /** Written only while the stored survey still has the version that was checked. */
    private void write(boolean exists, CampusSurveyDocument next, long checked) {
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
                .set("measures", next.measures())
                .set("version", next.version())
                .set("updatedAt", next.updatedAt());
        if (mongo.updateFirst(query, update, CampusSurveyDocument.class).getModifiedCount() == 0) {
            throw stale(checked + 1, checked);
        }
    }

    private static MapConflictException stale(long stored, long sent) {
        return new MapConflictException(
                "The survey was saved from somewhere else since you opened it. Reload to see those distances.",
                List.of(new ApiError.FieldIssue("version", "you sent %d, the survey is at %d".formatted(sent, stored))));
    }

    /**
     * The distance as sent, keeping the time it was taken when nothing about it changed. Asking for
     * it to be taken again is not taking it: the time stays.
     */
    private static SurveyMeasure toMeasure(SurveyMeasureDto dto, SurveyMeasure before, Instant now) {
        String label = blankToNull(dto.label());
        String text = blankToNull(dto.text());
        String note = blankToNull(dto.note());
        boolean same = before != null
                && Objects.equals(before.label(), label)
                && Objects.equals(before.text(), text)
                && Objects.equals(before.metres(), dto.metres())
                && Objects.equals(before.note(), note);
        return new SurveyMeasure(dto.id(), label, text, dto.metres(), note, dto.recheck(), same ? before.updatedAt() : now);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static SurveyResponse toResponse(CampusSurveyDocument document) {
        return new SurveyResponse(document.campus(),
                document.measures().stream()
                        .map(m -> new SurveyMeasureDto(m.id(), m.label(), m.text(), m.metres(), m.note(), m.recheck(), m.updatedAt()))
                        .toList(),
                document.version(), document.updatedAt());
    }
}

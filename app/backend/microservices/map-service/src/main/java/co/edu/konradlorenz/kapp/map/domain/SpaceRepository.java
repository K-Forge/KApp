package co.edu.konradlorenz.kapp.map.domain;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spaces by code, by building and by floor. The full-text search is not here: it needs the
 * {@code $meta} text score for ranking, which derived query methods cannot express, so it
 * lives in {@link co.edu.konradlorenz.kapp.map.service.SpaceSearch}.
 */
public interface SpaceRepository extends MongoRepository<SpaceDocument, String> {

    /**
     * Every space carrying this code, across all buildings. More than one result is a normal
     * situation - codes are unique per building - and is what makes the {@code buildingCode}
     * disambiguator necessary.
     */
    List<SpaceDocument> findByCodeOrderByBuildingCodeAsc(String code);

    Optional<SpaceDocument> findByBuildingIdAndCode(String buildingId, String code);

    Optional<SpaceDocument> findByBuildingIdAndDoorCode(String buildingId, String doorCode);

    List<SpaceDocument> findByBuildingIdAndFloorCodeOrderByCodeAsc(String buildingId, String floorCode);

    List<SpaceDocument> findByBuildingIdAndCodeIn(String buildingId, Collection<String> codes);

    List<SpaceDocument> findByBuildingIdAndAccessVia(String buildingId, String accessVia);

    boolean existsByBuildingId(String buildingId);

    boolean existsByBuildingIdAndFloorCode(String buildingId, String floorCode);

    boolean existsByBuildingIdAndWing(String buildingId, String wing);

    boolean existsByTypeCode(String typeCode);

    List<SpaceDocument> findByBuildingId(String buildingId);

    long countByPlaceholderIsTrue();
}

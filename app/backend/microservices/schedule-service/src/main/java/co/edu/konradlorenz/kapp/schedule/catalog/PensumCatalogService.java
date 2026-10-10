package co.edu.konradlorenz.kapp.schedule.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Which item of the student's pensum each course of their timetable is, so a client can tell which
 * square of the semáforo a class belongs to.
 *
 * <ul>
 *   <li><b>Cached a few minutes</b>: a pensum does not change during a semester, and a timetable
 *       should not cost a call to semaphore-service each time it is read.</li>
 *   <li><b>Fails open</b>: with semaphore-service down and nothing cached, the timetable is still
 *       served, each {@code pensumItemCode} null. Showing a student their classes is this service's
 *       job; matching them to the pensum is a courtesy.</li>
 * </ul>
 */
@Service
public class PensumCatalogService {

    private static final Logger log = LoggerFactory.getLogger(PensumCatalogService.class);

    private final CatalogClient client;

    public PensumCatalogService(CatalogClient client) {
        this.client = client;
    }

    @Cacheable(cacheNames = "pensumCourses", unless = "#result.isEmpty()")
    public List<PensumCourseView> pensumCourses(String pensumCode) {
        try {
            return client.listPensumCourses(pensumCode);
        } catch (RuntimeException e) {
            log.warn("Could not read pensum {} from semaphore-service; serving the timetable without "
                    + "its pensum items", pensumCode, e);
            return List.of();
        }
    }

    /**
     * @return the item of the pensum with that SINU code - by {@code sinuCode}, or by the printed code
     * where SINU's is not known - and empty when the catalogue could not be read or the course is not
     * in this pensum
     */
    public Optional<String> pensumItemCode(String pensumCode, String sinuCode) {
        if (pensumCode == null || sinuCode == null) {
            return Optional.empty();
        }
        List<PensumCourseView> items = pensumCourses(pensumCode);
        return items.stream().filter(item -> sinuCode.equals(item.sinuCode())).findFirst()
                .or(() -> items.stream().filter(item -> item.sinuCode() == null && sinuCode.equals(item.code()))
                        .findFirst())
                .map(PensumCourseView::pensumItemCode);
    }
}

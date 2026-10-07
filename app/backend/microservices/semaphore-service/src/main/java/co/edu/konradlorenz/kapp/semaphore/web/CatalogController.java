package co.edu.konradlorenz.kapp.semaphore.web;

import co.edu.konradlorenz.kapp.common.academic.ValidAcademicPeriod;
import co.edu.konradlorenz.kapp.semaphore.security.AdminOnly;
import co.edu.konradlorenz.kapp.semaphore.security.CatalogRead;
import co.edu.konradlorenz.kapp.semaphore.service.CatalogService;
import co.edu.konradlorenz.kapp.semaphore.service.PensumCsvImporter;
import co.edu.konradlorenz.kapp.semaphore.web.dto.ElectiveOfferingDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumCourseDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumImportReport;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumSummary;
import co.edu.konradlorenz.kapp.semaphore.web.dto.ProgramResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The academic catalog: programs, pensums and each semester's elective bank, as SINU publishes them.
 *
 * <p>Read-only, for any authenticated role except {@code ROLE_GUEST}. The one write is the import of
 * the catalog's backup, for {@code ROLE_ADMIN}. See {@code docs/api/semaphore.openapi.yaml}.
 */
@RestController
@RequestMapping("/api/catalog")
@Validated
@Tag(name = "Catalog")
public class CatalogController {

    private final CatalogService catalog;
    private final PensumCsvImporter importer;

    public CatalogController(CatalogService catalog, PensumCsvImporter importer) {
        this.catalog = catalog;
        this.importer = importer;
    }

    @GetMapping("/programs")
    @CatalogRead
    @Operation(summary = "List academic programs")
    public List<ProgramResponse> listPrograms() {
        return catalog.listPrograms();
    }

    @GetMapping("/programs/{programCode}")
    @CatalogRead
    @Operation(summary = "Get one academic program")
    public ProgramResponse getProgram(@PathVariable String programCode) {
        return catalog.getProgram(programCode);
    }

    @GetMapping("/pensums")
    @CatalogRead
    @Operation(summary = "List the pensums",
            description = "Summaries without their items: active plans first, then by program and code.")
    public List<PensumSummary> listPensums() {
        return catalog.listPensums();
    }

    @GetMapping("/pensums/{pensumCode}")
    @CatalogRead
    @Operation(summary = "Get one pensum, with every item")
    public PensumDto getPensum(@PathVariable String pensumCode) {
        return catalog.getPensum(pensumCode);
    }

    @GetMapping("/pensums/{pensumCode}/courses")
    @CatalogRead
    @Operation(summary = "List the items of a pensum")
    public List<PensumCourseDto> listPensumCourses(
            @PathVariable String pensumCode,
            @RequestParam(required = false) @Min(1) @Max(12) Integer level,
            @RequestParam(required = false) @Size(max = 20) String area,
            @RequestParam(required = false) Boolean isElectiveSlot) {
        return catalog.listPensumCourses(pensumCode, level, area, isElectiveSlot);
    }

    @GetMapping("/pensums/{pensumCode}/electives")
    @CatalogRead
    @Operation(summary = "List a semester's elective bank",
            description = "The courses SINU offers in the period to fill the pensum's elective slots. "
                    + "The current period when none is given.")
    public List<ElectiveOfferingDto> listPensumElectives(
            @PathVariable String pensumCode,
            @RequestParam(required = false) @ValidAcademicPeriod String period) {
        return catalog.listElectives(pensumCode, period);
    }

    /**
     * Loads the catalog's backup from a spreadsheet export: the only write the catalog takes.
     *
     * <p>Read as UTF-8 explicitly rather than through the platform default: these files come
     * off machines whose default may be anything, and a mis-decoded "Matemáticas" would be
     * stored wrong and only noticed by a student reading their own semáforo.
     */
    @PostMapping(value = "/pensums/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminOnly
    @Operation(summary = "Load the catalog backup from a CSV",
            description = "Validates the whole file first; nothing is written unless everything "
                    + "passes. Use dryRun=true to check a file without importing it.")
    public PensumImportReport importPensums(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun) throws IOException {
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return importer.importFrom(reader, dryRun);
        }
    }
}

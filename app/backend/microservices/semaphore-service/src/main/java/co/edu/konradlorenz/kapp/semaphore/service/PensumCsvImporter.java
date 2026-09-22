package co.edu.konradlorenz.kapp.semaphore.service;

import co.edu.konradlorenz.kapp.common.error.ApiError;
import co.edu.konradlorenz.kapp.common.error.BusinessRuleException;
import co.edu.konradlorenz.kapp.semaphore.domain.PensumStatus;
import co.edu.konradlorenz.kapp.semaphore.domain.Program;
import co.edu.konradlorenz.kapp.semaphore.domain.ProgramLevel;
import co.edu.konradlorenz.kapp.semaphore.domain.WeeklyHours;
import co.edu.konradlorenz.kapp.semaphore.repository.PensumRepository;
import co.edu.konradlorenz.kapp.semaphore.repository.ProgramRepository;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumAreaDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumCourseDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumDto;
import co.edu.konradlorenz.kapp.semaphore.web.dto.PensumImportReport;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads pensums in bulk from a spreadsheet export.
 *
 * <p>There are 24 programs and roughly 1200 rows. That is data-entry work, not programming, and
 * it has to be something a teammate can do in parallel without touching code - which is what
 * this endpoint is for. The same shape is what a SINU export would be mapped onto the day one
 * arrives.
 *
 * <h2>Nothing is written until everything validates</h2>
 * The whole file is parsed, assembled and validated first. A partial import would leave the
 * catalogue in a state nobody chose: half a pensum loaded, the rest rejected, and no way to tell
 * which rows made it without reading the database. Every failure is reported against the line
 * number of the file, because that is what the person fixing it is looking at.
 *
 * <h2>Declared totals are checked, not trusted</h2>
 * Each row repeats the pensum's declared credits and hours, and the import adds up the courses
 * and compares. The Ingeniería de Sistemas transcription came to 144 credits against 142
 * declared. Accepting that silently would have put the discrepancy in front of a student months
 * later; refusing it puts it in front of whoever is doing the transcription, now.
 */
@Service
public class PensumCsvImporter {

    private static final Logger log = LoggerFactory.getLogger(PensumCsvImporter.class);

    /**
     * Enough issues to fix a whole batch in one pass, few enough that the response stays
     * readable. The message says how many more there were.
     */
    private static final int MAX_REPORTED_ISSUES = 200;

    static final List<String> COLUMNS = List.of(
            "programCode", "programName", "faculty", "programLevel",
            "pensumCode", "reform", "pensumStatus", "declaredCredits", "declaredHours", "levels",
            "areaCode", "areaName", "areaColor",
            "pensumItemCode", "courseCode", "courseName", "courseLevel",
            "credits", "weeklyHours", "isElectiveSlot", "prerequisites", "sinuCode");

    private final ProgramRepository programs;
    private final PensumRepository pensums;
    private final PensumValidator validator;
    /**
     * The same bean validator the write endpoints use.
     *
     * <p>Without it the two paths disagreed: the import wrote area codes the PUT then refused,
     * so a pensum created here could not be edited afterwards. Whatever the annotations say,
     * both doors now say it.
     */
    private final Validator beanValidator;

    public PensumCsvImporter(ProgramRepository programs, PensumRepository pensums,
                                  PensumValidator validator, Validator beanValidator) {
        this.programs = programs;
        this.pensums = pensums;
        this.validator = validator;
        this.beanValidator = beanValidator;
    }

    public PensumImportReport importFrom(Reader csv, boolean dryRun) {
        List<ApiError.FieldIssue> issues = new ArrayList<>();
        Map<String, PensumDraft> drafts = new LinkedHashMap<>();
        int rowsRead = 0;

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(COLUMNS.toArray(String[]::new))
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .build();

        try (CSVParser parser = CSVParser.parse(csv, format)) {
            for (CSVRecord record : parser) {
                rowsRead++;
                // getRecordNumber() counts from the first parsed record; +1 puts it back on the
                // line the person is looking at in their spreadsheet, header included.
                long line = record.getRecordNumber() + 1;
                readRow(record, line, drafts, issues);
            }
        } catch (IOException e) {
            throw new BusinessRuleException("Could not read the file: " + e.getMessage(),
                    List.of(new ApiError.FieldIssue("file", "unreadable")));
        } catch (IllegalArgumentException e) {
            // Commons CSV reports a row with the wrong number of columns this way.
            throw new BusinessRuleException("The file is not valid CSV: " + e.getMessage(),
                    List.of(new ApiError.FieldIssue("file", e.getMessage())));
        }

        if (drafts.isEmpty() && issues.isEmpty()) {
            throw new BusinessRuleException("The file has no data rows",
                    List.of(new ApiError.FieldIssue("file", "only a header, or empty")));
        }

        List<PensumImportReport.ImportedPensum> summaries = new ArrayList<>();
        List<PensumDto> ready = new ArrayList<>();
        List<Program> programsToWrite = new ArrayList<>();

        for (PensumDraft draft : drafts.values()) {
            // Its header never parsed, so its totals are zero and its courses were skipped.
            // Anything said about it here would be noise on top of the one real message.
            if (draft.headerBroken) {
                continue;
            }
            PensumDto dto = draft.toDto();

            int computedCredits = dto.courses().stream().mapToInt(PensumCourseDto::credits).sum();
            // A pensum's `totalHours` is the sum of WEEKLY hours, not of contact hours.
            // The seeded Ingeniería de Sistemas plan declares 194 against 197 actual weekly
            // hours; summing weeklyHours * 16 instead would have produced 3152 and reported
            // every correct file as broken.
            double computedHours = dto.courses().stream()
                    .mapToDouble(PensumCourseDto::weeklyHours).sum();

            if (computedCredits != draft.declaredCredits) {
                issues.add(new ApiError.FieldIssue(
                        "pensum " + draft.pensumCode + " · declaredCredits",
                        "the file declares %d but its courses add up to %d"
                                .formatted(draft.declaredCredits, computedCredits)));
            }
            if (computedHours != draft.declaredHours) {
                issues.add(new ApiError.FieldIssue(
                        "pensum " + draft.pensumCode + " · declaredHours",
                        "the file declares %s but its courses add up to %s".formatted(
                                WeeklyHours.format(draft.declaredHours),
                                WeeklyHours.format(computedHours))));
            }

            // The annotations first - lengths, blanks, the colour pattern - then the rules that
            // need the whole document. Reported the same way, labelled with the pensum, because
            // one file carries many.
            for (ConstraintViolation<PensumDto> v : beanValidator.validate(dto)) {
                issues.add(new ApiError.FieldIssue(
                        "pensum " + draft.pensumCode + " · " + v.getPropertyPath(), v.getMessage()));
            }

            try {
                validator.validate(dto);
            } catch (BusinessRuleException e) {
                // Re-labelled with the pensum, since one file carries many.
                e.getDetails().forEach(issue -> issues.add(new ApiError.FieldIssue(
                        "pensum " + draft.pensumCode + " · " + issue.field(), issue.issue())));
            }

            ready.add(dto);
            programsToWrite.add(draft.toProgram());
            summaries.add(new PensumImportReport.ImportedPensum(
                    draft.pensumCode, draft.programCode, draft.programName, dto.courses().size(),
                    draft.declaredCredits, computedCredits, draft.declaredHours, computedHours,
                    !programs.existsById(draft.programCode),
                    !pensums.existsById(draft.pensumCode)));
        }

        if (!issues.isEmpty()) {
            throw new BusinessRuleException(summariseFailure(issues), capped(issues));
        }

        if (!dryRun) {
            programsToWrite.forEach(programs::save);
            ready.forEach(dto -> pensums.save(dto.toDomain()));
            log.info("Imported {} pensums from CSV ({} rows)", ready.size(), rowsRead);
        }

        return new PensumImportReport(dryRun, rowsRead, summaries);
    }

    private void readRow(CSVRecord record, long line, Map<String, PensumDraft> drafts,
                          List<ApiError.FieldIssue> issues) {
        String pensumCode = record.get("pensumCode");
        if (pensumCode == null || pensumCode.isBlank()) {
            issues.add(issue(line, "pensumCode", "is required on every row"));
            return;
        }

        PensumDraft draft = drafts.get(pensumCode);
        if (draft == null) {
            draft = new PensumDraft(pensumCode);
            drafts.put(pensumCode, draft);
            try {
                draft.readHeader(record);
            } catch (RuntimeException e) {
                // The first row's header is what every later row is compared against and what
                // the declared totals are checked against. Half-read, the fields it never
                // reached are still 0 - and that 0 was then reported as a disagreement on
                // every remaining row and as a total that does not add up. One empty `reform`
                // produced eleven messages, ten of them pointing at rows that were fine and
                // none of them naming the cause. Say it once and stop checking this pensum.
                draft.headerBroken = true;
                issues.add(issue(line, "pensum header", e.getMessage()));
                return;
            }
        } else if (draft.headerBroken) {
            return;
        } else {
            // Every row repeats the pensum's header. Two rows disagreeing means somebody
            // edited one and not the others, which is worth catching before it becomes
            // "whichever row happened to be first wins".
            draft.checkHeaderMatches(record, line, issues);
        }

        try {
            draft.addCourse(record);
        } catch (RuntimeException e) {
            issues.add(issue(line, "course", e.getMessage()));
        }
        draft.addArea(record, line, issues);
    }

    private static ApiError.FieldIssue issue(long line, String field, String message) {
        return new ApiError.FieldIssue("row " + line + " · " + field, message);
    }

    private static String summariseFailure(List<ApiError.FieldIssue> issues) {
        return issues.size() <= MAX_REPORTED_ISSUES
                ? "The file was not imported: %d problem(s) found. Nothing was written."
                        .formatted(issues.size())
                : ("The file was not imported: %d problem(s) found, the first %d are listed. "
                        + "Nothing was written.").formatted(issues.size(), MAX_REPORTED_ISSUES);
    }

    private static List<ApiError.FieldIssue> capped(List<ApiError.FieldIssue> issues) {
        return issues.size() <= MAX_REPORTED_ISSUES
                ? issues
                : issues.subList(0, MAX_REPORTED_ISSUES);
    }

    /** One pensum being assembled out of the rows that mention it. */
    private static final class PensumDraft {
        private final String pensumCode;
        private String programCode;
        private String programName;
        private String faculty;
        private ProgramLevel programLevel;
        private String reform;
        private PensumStatus status;
        /** True once the first row failed to parse: nothing about this pensum can be trusted. */
        private boolean headerBroken;
        private int declaredCredits;
        private double declaredHours;
        private int levels;

        private final Map<String, PensumAreaDto> areas = new LinkedHashMap<>();
        private final Map<String, double[]> areaTotals = new LinkedHashMap<>();
        private final List<PensumCourseDto> courses = new ArrayList<>();
        private final Set<String> seenItemCodes = new LinkedHashSet<>();

        PensumDraft(String pensumCode) {
            this.pensumCode = pensumCode;
        }

        void readHeader(CSVRecord r) {
            programCode = required(r, "programCode");
            programName = required(r, "programName");
            faculty = required(r, "faculty");
            programLevel = enumValue(ProgramLevel.class, required(r, "programLevel"), "programLevel");
            reform = required(r, "reform");
            status = enumValue(PensumStatus.class, required(r, "pensumStatus"), "pensumStatus");
            declaredCredits = integer(r, "declaredCredits");
            declaredHours = hours(r, "declaredHours");
            levels = integer(r, "levels");
        }

        void checkHeaderMatches(CSVRecord r, long line, List<ApiError.FieldIssue> issues) {
            compare(line, issues, "programCode", programCode, r.get("programCode"));
            compare(line, issues, "declaredCredits", String.valueOf(declaredCredits),
                    r.get("declaredCredits"));
            compareHours(line, issues, "declaredHours", declaredHours, r.get("declaredHours"));
        }

        /**
         * The same check as {@link #compare}, on the number rather than the text: a spreadsheet
         * writes the first row's 194 and a later row's 194.0 for the same figure, and the
         * Spanish locale writes 4,5 where another row says 4.5. Comparing the text would call
         * those disagreements.
         */
        private void compareHours(long line, List<ApiError.FieldIssue> issues, String field,
                                   double expected, String actual) {
            if (actual == null || actual.isBlank()) {
                return;
            }
            try {
                if (WeeklyHours.parse(actual) != expected) {
                    issues.add(issue(line, field,
                            "disagrees with the first row of pensum %s, which says '%s'"
                                    .formatted(pensumCode, WeeklyHours.format(expected))));
                }
            } catch (IllegalArgumentException e) {
                issues.add(issue(line, field, e.getMessage()));
            }
        }

        private void compare(long line, List<ApiError.FieldIssue> issues, String field,
                              String expected, String actual) {
            if (actual != null && !actual.isBlank() && !expected.equals(actual.trim())) {
                issues.add(issue(line, field,
                        "disagrees with the first row of pensum %s, which says '%s'"
                                .formatted(pensumCode, expected)));
            }
        }

        void addArea(CSVRecord r, long line, List<ApiError.FieldIssue> issues) {
            String code = r.get("areaCode");
            if (code == null || code.isBlank()) {
                issues.add(issue(line, "areaCode", "is required on every row"));
                return;
            }
            code = code.trim();
            // Area credits and hours are SUMMED from the courses rather than transcribed.
            // They are arithmetic on data the file already carries, and asking a person to
            // restate them only creates one more thing that can be wrong.
            double[] totals = areaTotals.computeIfAbsent(code, c -> new double[2]);
            totals[0] += intOrZero(r, "credits");
            totals[1] += hoursOrZero(r, "weeklyHours");

            String name = r.get("areaName");
            String color = r.get("areaColor");
            areas.putIfAbsent(code, new PensumAreaDto(code,
                    name == null || name.isBlank() ? code : name.trim(),
                    color == null || color.isBlank() ? "#888888" : color.trim(), 0, 0));
        }

        void addCourse(CSVRecord r) {
            String itemCode = required(r, "pensumItemCode");
            if (!seenItemCodes.add(itemCode)) {
                throw new IllegalArgumentException(
                        "pensumItemCode '%s' appears twice in pensum %s".formatted(itemCode, pensumCode));
            }
            boolean elective = booleanValue(r, "isElectiveSlot");
            String code = blankToNull(r.get("courseCode"));

            courses.add(new PensumCourseDto(
                    code,
                    itemCode,
                    required(r, "courseName"),
                    integer(r, "courseLevel"),
                    integer(r, "credits"),
                    hours(r, "weeklyHours"),
                    null, // derived as weeklyHours * 16 by the validator's own invariant
                    required(r, "areaCode"),
                    elective,
                    prerequisites(r),
                    blankToNull(r.get("sinuCode"))));
        }

        PensumDto toDto() {
            List<PensumAreaDto> withTotals = areas.values().stream()
                    .map(a -> {
                        double[] t = areaTotals.getOrDefault(a.code(), new double[2]);
                        return new PensumAreaDto(a.code(), a.name(), a.color(), (int) t[0], t[1]);
                    })
                    .toList();
            return new PensumDto(pensumCode, programCode, programName, faculty, reform,
                    status, declaredCredits, declaredHours, levels, withTotals, courses);
        }

        Program toProgram() {
            return new Program(programCode, programName, faculty, programLevel);
        }

        private static List<String> prerequisites(CSVRecord r) {
            String raw = r.get("prerequisites");
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            // Semicolons, not commas: a comma inside a CSV cell has to be quoted, and a
            // spreadsheet will happily drop the quotes on a round trip.
            return Arrays.stream(raw.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        private static String required(CSVRecord r, String column) {
            String value = r.get(column);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(column + " is required");
            }
            return value.trim();
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }

        private static int integer(CSVRecord r, String column) {
            String value = required(r, column);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "%s must be a whole number, got '%s'".formatted(column, value));
            }
        }

        private static int intOrZero(CSVRecord r, String column) {
            try {
                return integer(r, column);
            } catch (RuntimeException e) {
                return 0;
            }
        }

        /**
         * Hours, which unlike every other figure in the file may carry a half: four practices
         * across the published plans print one. A cell holding the Spanish 4,5 has to be quoted
         * for the file to still be CSV, and a spreadsheet that drops those quotes breaks the row
         * into two - which Commons CSV reports as a column count, before this is reached.
         */
        private static double hours(CSVRecord r, String column) {
            String value = required(r, column);
            try {
                return WeeklyHours.parse(value);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("%s %s".formatted(column, e.getMessage()));
            }
        }

        private static double hoursOrZero(CSVRecord r, String column) {
            try {
                return hours(r, column);
            } catch (RuntimeException e) {
                return 0;
            }
        }

        private static boolean booleanValue(CSVRecord r, String column) {
            String value = r.get(column);
            if (value == null || value.isBlank()) {
                return false;
            }
            return switch (value.trim().toLowerCase()) {
                case "true", "1", "si", "sí", "yes", "x" -> true;
                case "false", "0", "no", "" -> false;
                default -> throw new IllegalArgumentException(
                        "%s must be true or false, got '%s'".formatted(column, value));
            };
        }

        private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String column) {
            try {
                return Enum.valueOf(type, value.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("%s must be one of %s, got '%s'".formatted(
                        column, Arrays.toString(type.getEnumConstants()), value));
            }
        }
    }
}

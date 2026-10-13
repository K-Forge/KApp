package co.edu.konradlorenz.kapp.semaphore.web.dto;

import java.util.List;

/**
 * What a bulk pensum import did, or would have done.
 *
 * @param dryRun   when true, nothing was written
 * @param rowsRead data rows in the file, header excluded
 * @param pensums one entry per pensum found in the file
 */
public record PensumImportReport(
        boolean dryRun,
        int rowsRead,
        List<ImportedPensum> pensums
) {

    /**
     * @param declaredCredits the total the file states for the pensum
     * @param computedCredits the total actually obtained by adding up its courses. When the two
     *                        differ the import is refused - the transcription of Ingeniería de
     *                        Sistemas produced 144 against 142 declared, and that kind of error
     *                        has to surface at import time rather than months later when a
     *                        student wonders why their semáforo does not add up
     */
    public record ImportedPensum(
            String pensumCode,
            String programCode,
            String programName,
            int courses,
            int declaredCredits,
            int computedCredits,
            double declaredHours,
            double computedHours,
            boolean programCreated,
            boolean pensumCreated
    ) {
    }
}

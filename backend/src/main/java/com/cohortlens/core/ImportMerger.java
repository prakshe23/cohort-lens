package com.cohortlens.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Applies the "append" rule: rows for a student and term that are already stored are rejected
 * (with a clear message) instead of silently overwriting data. To load a corrected file over old
 * data, use the replace mode instead, or correct single values through the audited correction endpoint.
 */
public final class ImportMerger {
    private ImportMerger() {
    }

    public static ImportResult rejectExisting(ImportResult result, Set<String> existingKeys) {
        if (result.fatal() || existingKeys.isEmpty()) {
            return result;
        }
        List<Observation> accepted = new ArrayList<>();
        List<Integer> rowNumbers = new ArrayList<>();
        List<ValidationIssue> issues = new ArrayList<>(result.issues());
        int rejected = result.rejectedRows();

        for (int i = 0; i < result.accepted().size(); i++) {
            Observation o = result.accepted().get(i);
            int rowNumber = result.acceptedRowNumbers().get(i);
            if (existingKeys.contains(o.naturalKey())) {
                issues.add(new ValidationIssue(rowNumber, "student_id", Severity.ERROR, "DUPLICATE_EXISTING",
                        "This student and term is already stored. Use replace mode to load a corrected file.", ""));
                rejected++;
            } else {
                accepted.add(o);
                rowNumbers.add(rowNumber);
            }
        }
        issues.sort(java.util.Comparator.comparingInt(ValidationIssue::rowNumber));
        return new ImportResult(accepted, rowNumbers, issues, result.totalRows(), rejected, false, null);
    }
}

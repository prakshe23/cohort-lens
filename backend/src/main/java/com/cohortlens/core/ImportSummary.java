package com.cohortlens.core;

import java.util.Map;

/** What the API returns about one import job. */
public record ImportSummary(
        long id,
        String filename,
        String mode,
        String status,
        int totalRows,
        int acceptedRows,
        int rejectedRows,
        long warnings,
        long errors,
        Map<String, Long> issueCounts,
        String message,
        String createdAt,
        String createdBy) {

    public static ImportSummary of(long id, String filename, String mode, ImportResult r, String createdAt,
                                   String createdBy) {
        return new ImportSummary(id, filename, mode, r.fatal() ? "FAILED" : "COMPLETED", r.totalRows(),
                r.acceptedRows(), r.rejectedRows(), r.warningCount(), r.errorCount(), r.issueCounts(),
                r.fatal() ? r.fatalMessage() : null, createdAt, createdBy);
    }
}

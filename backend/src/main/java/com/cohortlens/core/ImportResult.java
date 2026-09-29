package com.cohortlens.core;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Outcome of validating one uploaded CSV. */
public record ImportResult(
        List<Observation> accepted,
        List<Integer> acceptedRowNumbers,
        List<ValidationIssue> issues,
        int totalRows,
        int rejectedRows,
        boolean fatal,
        String fatalMessage) {

    public int acceptedRows() {
        return accepted.size();
    }

    public long warningCount() {
        return issues.stream().filter(i -> i.severity() == Severity.WARNING).count();
    }

    public long errorCount() {
        return issues.stream().filter(i -> i.severity() == Severity.ERROR).count();
    }

    /** Issue counts per code, sorted by code, handy for a summary view. */
    public Map<String, Long> issueCounts() {
        Map<String, Long> counts = new TreeMap<>();
        for (ValidationIssue issue : issues) {
            counts.merge(issue.code(), 1L, Long::sum);
        }
        return counts;
    }

    static ImportResult fatal(String message) {
        return new ImportResult(List.of(), List.of(), List.of(), 0, 0, true, message);
    }
}

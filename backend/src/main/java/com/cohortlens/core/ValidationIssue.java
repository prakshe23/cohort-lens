package com.cohortlens.core;

/** A problem found in one row of an uploaded file. */
public record ValidationIssue(
        int rowNumber,
        String field,
        Severity severity,
        String code,
        String message,
        String rawValue) {
}

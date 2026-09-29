package com.cohortlens.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates an uploaded CSV of student observations.
 *
 * <p>Rules, in short: a missing required column stops the whole import. A row with a hard problem
 * (bad grade, bad term, score outside 0 to 100, duplicate student and term, and so on) is rejected.
 * A row with a soft problem (a blank score, an attendance rate written as a percent) is kept and
 * flagged with a warning, and the fix that was applied is recorded. Every problem is reported with
 * its row number so the person who owns the data can fix it at the source.
 */
public final class ImportProcessor {

    public static final List<String> REQUIRED_COLUMNS = List.of(
            "student_id", "school", "grade", "academic_year", "term", "economic_status",
            "english_learner", "math_score", "reading_score", "attendance_rate", "discipline_incidents");

    static final int MIN_GRADE = 0;
    static final int MAX_GRADE = 12;
    static final int MIN_YEAR = 1990;
    static final int MAX_YEAR = 2100;

    private final Pseudonymizer pseudonymizer;

    public ImportProcessor(Pseudonymizer pseudonymizer) {
        this.pseudonymizer = pseudonymizer;
    }

    public ImportResult process(String csvText) {
        List<String[]> rows = CsvParser.parse(stripBom(csvText));
        if (rows.isEmpty()) {
            return ImportResult.fatal("The file is empty.");
        }

        Map<String, Integer> columns = new HashMap<>();
        String[] header = rows.get(0);
        for (int i = 0; i < header.length; i++) {
            columns.putIfAbsent(header[i].trim().toLowerCase(Locale.ROOT), i);
        }
        List<String> missing = new ArrayList<>();
        for (String required : REQUIRED_COLUMNS) {
            if (!columns.containsKey(required)) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            return ImportResult.fatal("Missing required column(s): " + String.join(", ", missing));
        }

        List<Observation> accepted = new ArrayList<>();
        List<Integer> acceptedRowNumbers = new ArrayList<>();
        List<ValidationIssue> issues = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        int totalRows = 0;
        int rejected = 0;

        for (int r = 1; r < rows.size(); r++) {
            String[] cells = rows.get(r);
            if (isBlankRow(cells)) {
                continue;
            }
            totalRows++;
            int rowNumber = r + 1; // header is row 1
            List<ValidationIssue> rowIssues = new ArrayList<>();

            if (cells.length != header.length) {
                rowIssues.add(new ValidationIssue(rowNumber, "row", Severity.ERROR, "MALFORMED_ROW",
                        "Expected " + header.length + " columns but found " + cells.length, ""));
                issues.addAll(rowIssues);
                rejected++;
                continue;
            }

            Observation observation = parseRow(rowNumber, cells, columns, rowIssues);
            boolean hasError = rowIssues.stream().anyMatch(i -> i.severity() == Severity.ERROR);

            if (!hasError && observation != null) {
                if (!seenKeys.add(observation.naturalKey())) {
                    rowIssues.add(new ValidationIssue(rowNumber, "student_id", Severity.ERROR, "DUPLICATE_ROW",
                            "This student already has a row for the same academic year and term", ""));
                    hasError = true;
                }
            }

            issues.addAll(rowIssues);
            if (hasError || observation == null) {
                rejected++;
            } else {
                accepted.add(observation);
                acceptedRowNumbers.add(rowNumber);
            }
        }
        return new ImportResult(accepted, acceptedRowNumbers, issues, totalRows, rejected, false, null);
    }

    private Observation parseRow(int rowNumber, String[] cells, Map<String, Integer> columns,
                                 List<ValidationIssue> issues) {
        String studentId = cell(cells, columns, "student_id");
        String school = cell(cells, columns, "school").replaceAll("\\s+", " ");
        if (studentId.isEmpty()) {
            issues.add(error(rowNumber, "student_id", "MISSING_STUDENT_ID", "Student id is required", studentId));
        }
        if (school.isEmpty()) {
            issues.add(error(rowNumber, "school", "MISSING_SCHOOL", "School is required", school));
        }

        Integer grade = parseInt(cell(cells, columns, "grade"));
        if (grade == null) {
            issues.add(error(rowNumber, "grade", "INVALID_GRADE", "Grade must be a whole number",
                    cell(cells, columns, "grade")));
        } else if (grade < MIN_GRADE || grade > MAX_GRADE) {
            issues.add(error(rowNumber, "grade", "GRADE_OUT_OF_RANGE",
                    "Grade must be between " + MIN_GRADE + " and " + MAX_GRADE, String.valueOf(grade)));
        }

        Integer year = parseInt(cell(cells, columns, "academic_year"));
        if (year == null || year < MIN_YEAR || year > MAX_YEAR) {
            issues.add(error(rowNumber, "academic_year", "INVALID_YEAR",
                    "Academic year must be a four digit year", cell(cells, columns, "academic_year")));
        }

        Term term = parseTerm(cell(cells, columns, "term"));
        if (term == null) {
            issues.add(error(rowNumber, "term", "INVALID_TERM", "Term must be FALL or SPRING",
                    cell(cells, columns, "term")));
        }

        EconomicStatus status = parseEconomicStatus(cell(cells, columns, "economic_status"));
        if (status == null) {
            issues.add(error(rowNumber, "economic_status", "INVALID_ECONOMIC_STATUS",
                    "Economic status must be LOW_INCOME or NOT_LOW_INCOME", cell(cells, columns, "economic_status")));
        }

        Boolean englishLearner = parseBoolean(cell(cells, columns, "english_learner"));
        if (englishLearner == null) {
            issues.add(error(rowNumber, "english_learner", "INVALID_ENGLISH_LEARNER",
                    "English learner must be Y or N", cell(cells, columns, "english_learner")));
        }

        Double math = parseScore(rowNumber, "math_score", "MATH", cell(cells, columns, "math_score"), issues);
        Double reading = parseScore(rowNumber, "reading_score", "READING", cell(cells, columns, "reading_score"), issues);
        Double attendance = parseAttendance(rowNumber, cell(cells, columns, "attendance_rate"), issues);
        Integer discipline = parseDiscipline(rowNumber, cell(cells, columns, "discipline_incidents"), issues);

        boolean hasError = issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
        if (hasError) {
            return null;
        }
        return new Observation(pseudonymizer.pseudonymize(studentId), school, grade, year, term, status,
                englishLearner, math, reading, attendance, discipline);
    }

    private Double parseScore(int rowNumber, String field, String label, String raw, List<ValidationIssue> issues) {
        if (raw.isEmpty()) {
            issues.add(new ValidationIssue(rowNumber, field, Severity.WARNING, "MISSING_" + label + "_SCORE",
                    "Score is blank and was left empty", raw));
            return null;
        }
        Double value = parseDouble(raw);
        if (value == null) {
            issues.add(error(rowNumber, field, "INVALID_SCORE", "Score must be a number", raw));
            return null;
        }
        if (value < 0 || value > 100) {
            issues.add(error(rowNumber, field, "SCORE_OUT_OF_RANGE", "Score must be between 0 and 100", raw));
            return null;
        }
        return value;
    }

    private Double parseAttendance(int rowNumber, String raw, List<ValidationIssue> issues) {
        if (raw.isEmpty()) {
            issues.add(new ValidationIssue(rowNumber, "attendance_rate", Severity.WARNING, "MISSING_ATTENDANCE",
                    "Attendance rate is blank and was left empty", raw));
            return null;
        }
        Double value = parseDouble(raw);
        if (value == null) {
            issues.add(error(rowNumber, "attendance_rate", "INVALID_ATTENDANCE", "Attendance must be a number", raw));
            return null;
        }
        if (value > 1 && value <= 100) {
            issues.add(new ValidationIssue(rowNumber, "attendance_rate", Severity.WARNING,
                    "ATTENDANCE_PERCENT_NORMALIZED", "Looked like a percent, divided by 100", raw));
            return value / 100.0;
        }
        if (value < 0 || value > 1) {
            issues.add(error(rowNumber, "attendance_rate", "ATTENDANCE_OUT_OF_RANGE",
                    "Attendance must be between 0 and 1 (or 0 and 100 as a percent)", raw));
            return null;
        }
        return value;
    }

    private Integer parseDiscipline(int rowNumber, String raw, List<ValidationIssue> issues) {
        if (raw.isEmpty()) {
            issues.add(new ValidationIssue(rowNumber, "discipline_incidents", Severity.WARNING,
                    "MISSING_DISCIPLINE", "Blank count treated as 0", raw));
            return 0;
        }
        Integer value = parseInt(raw);
        if (value == null || value < 0) {
            issues.add(error(rowNumber, "discipline_incidents", "INVALID_DISCIPLINE",
                    "Incident count must be a whole number of 0 or more", raw));
            return null;
        }
        return value;
    }

    private static Term parseTerm(String raw) {
        String v = raw.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "FALL" -> Term.FALL;
            case "SPRING" -> Term.SPRING;
            default -> null;
        };
    }

    private static EconomicStatus parseEconomicStatus(String raw) {
        String v = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (v) {
            case "LOW_INCOME" -> EconomicStatus.LOW_INCOME;
            case "NOT_LOW_INCOME" -> EconomicStatus.NOT_LOW_INCOME;
            default -> null;
        };
    }

    private static Boolean parseBoolean(String raw) {
        String v = raw.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "Y", "YES", "TRUE", "T", "1" -> Boolean.TRUE;
            case "N", "NO", "FALSE", "F", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDouble(String raw) {
        try {
            double v = Double.parseDouble(raw.trim());
            return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String cell(String[] cells, Map<String, Integer> columns, String name) {
        return cells[columns.get(name)].trim();
    }

    private static boolean isBlankRow(String[] cells) {
        for (String c : cells) {
            if (!c.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String stripBom(String text) {
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    private static ValidationIssue error(int rowNumber, String field, String code, String message, String raw) {
        String shown = raw == null ? "" : (raw.length() > 100 ? raw.substring(0, 100) : raw);
        return new ValidationIssue(rowNumber, field, Severity.ERROR, code, message, shown);
    }
}

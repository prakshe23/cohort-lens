package com.cohortlens.core;

import java.util.List;

/** Writes pseudonymized observations back out as CSV. The raw student id is not present and cannot be recovered. */
public final class CsvExporter {
    public static final String HEADER = "student_key,school,grade,academic_year,term,economic_status,"
            + "english_learner,math_score,reading_score,attendance_rate,discipline_incidents";

    private CsvExporter() {
    }

    public static String toCsv(List<Observation> observations) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (Observation o : observations) {
            sb.append(field(o.studentKey())).append(',')
                    .append(field(o.school())).append(',')
                    .append(o.grade()).append(',')
                    .append(o.academicYear()).append(',')
                    .append(o.term()).append(',')
                    .append(o.economicStatus()).append(',')
                    .append(o.englishLearner() ? "Y" : "N").append(',')
                    .append(o.mathScore() == null ? "" : o.mathScore()).append(',')
                    .append(o.readingScore() == null ? "" : o.readingScore()).append(',')
                    .append(o.attendanceRate() == null ? "" : o.attendanceRate()).append(',')
                    .append(o.disciplineIncidents()).append('\n');
        }
        return sb.toString();
    }

    /** Quote a text field if it contains a comma, quote or newline, and neutralize spreadsheet formulas. */
    static String field(String value) {
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v; // prevents a spreadsheet from running the cell as a formula
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}

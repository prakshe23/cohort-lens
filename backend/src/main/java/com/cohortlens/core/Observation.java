package com.cohortlens.core;

/**
 * One student in one term. The student is identified only by a pseudonymous key;
 * the raw student id from the source file is never kept.
 */
public record Observation(
        String studentKey,
        String school,
        int grade,
        int academicYear,
        Term term,
        EconomicStatus economicStatus,
        boolean englishLearner,
        Double mathScore,
        Double readingScore,
        Double attendanceRate,
        int disciplineIncidents) {

    public int termIndex() {
        return Term.index(academicYear, term);
    }

    /** Identifies one student in one term. Two rows with the same key are duplicates. */
    public String naturalKey() {
        return studentKey + "|" + termIndex();
    }
}

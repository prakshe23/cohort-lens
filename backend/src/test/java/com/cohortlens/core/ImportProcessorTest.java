package com.cohortlens.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ImportProcessorTest {
    private static final String HEADER = String.join(",", ImportProcessor.REQUIRED_COLUMNS);
    private final ImportProcessor processor = new ImportProcessor(new Pseudonymizer("a-test-secret-with-enough-length"));

    private static String row(String id, String term, String status, String el, String math, String reading,
                              String attendance, String discipline) {
        return String.join(",", id, "Maple Grove", "5", "2022", term, status, el, math, reading, attendance, discipline);
    }

    private ImportResult run(String... dataRows) {
        return processor.process(HEADER + "\n" + String.join("\n", dataRows) + "\n");
    }

    private static boolean hasCode(ImportResult result, String code) {
        return result.issues().stream().anyMatch(i -> i.code().equals(code));
    }

    @Test
    void acceptsAValidRow() {
        ImportResult r = run(row("S1", "FALL", "LOW_INCOME", "N", "72.5", "68", "0.95", "1"));
        assertFalse(r.fatal());
        assertEquals(1, r.acceptedRows());
        assertEquals(0, r.rejectedRows());
        assertEquals(0, r.issues().size());
        Observation o = r.accepted().get(0);
        assertEquals(72.5, o.mathScore().doubleValue());
        assertEquals(EconomicStatus.LOW_INCOME, o.economicStatus());
        assertEquals(Term.FALL, o.term());
    }

    @Test
    void missingRequiredColumnStopsTheImport() {
        ImportResult r = processor.process("student_id,school\nS1,Maple Grove\n");
        assertTrue(r.fatal());
        assertTrue(r.fatalMessage().contains("grade"));
        assertEquals(0, r.acceptedRows());
    }

    @Test
    void emptyFileIsFatal() {
        assertTrue(processor.process("").fatal());
    }

    @Test
    void scoreOutsideZeroToHundredIsRejected() {
        ImportResult r = run(row("S1", "FALL", "LOW_INCOME", "N", "105", "68", "0.95", "0"));
        assertEquals(0, r.acceptedRows());
        assertEquals(1, r.rejectedRows());
        assertTrue(hasCode(r, "SCORE_OUT_OF_RANGE"));
    }

    @Test
    void blankScoreIsKeptWithAWarning() {
        ImportResult r = run(row("S1", "FALL", "LOW_INCOME", "N", "", "68", "0.95", "0"));
        assertEquals(1, r.acceptedRows());
        assertNull(r.accepted().get(0).mathScore());
        assertTrue(hasCode(r, "MISSING_MATH_SCORE"));
        assertEquals(1, r.warningCount());
        assertEquals(0, r.errorCount());
    }

    @Test
    void attendanceWrittenAsPercentIsNormalized() {
        ImportResult r = run(row("S1", "FALL", "LOW_INCOME", "N", "70", "68", "93.5", "0"));
        assertEquals(1, r.acceptedRows());
        assertEquals(0.935, r.accepted().get(0).attendanceRate().doubleValue(), 1e-9);
        assertTrue(hasCode(r, "ATTENDANCE_PERCENT_NORMALIZED"));
    }

    @Test
    void invalidTermIsRejected() {
        ImportResult r = run(row("S1", "Winter", "LOW_INCOME", "N", "70", "68", "0.95", "0"));
        assertEquals(1, r.rejectedRows());
        assertTrue(hasCode(r, "INVALID_TERM"));
    }

    @Test
    void gradeOutOfRangeIsRejected() {
        String bad = "S1,Maple Grove,14,2022,FALL,LOW_INCOME,N,70,68,0.95,0";
        ImportResult r = processor.process(HEADER + "\n" + bad + "\n");
        assertEquals(1, r.rejectedRows());
        assertTrue(hasCode(r, "GRADE_OUT_OF_RANGE"));
    }

    @Test
    void secondCopyOfTheSameStudentAndTermIsRejected() {
        String line = row("S1", "FALL", "LOW_INCOME", "N", "70", "68", "0.95", "0");
        ImportResult r = run(line, line);
        assertEquals(1, r.acceptedRows());
        assertEquals(1, r.rejectedRows());
        assertTrue(hasCode(r, "DUPLICATE_ROW"));
    }

    @Test
    void sameStudentInDifferentTermsIsFine() {
        ImportResult r = run(row("S1", "FALL", "LOW_INCOME", "N", "70", "68", "0.95", "0"),
                row("S1", "SPRING", "LOW_INCOME", "N", "72", "69", "0.96", "0"));
        assertEquals(2, r.acceptedRows());
        assertEquals(r.accepted().get(0).studentKey(), r.accepted().get(1).studentKey());
    }

    @Test
    void sloppyCategoryValuesAreNormalized() {
        ImportResult r = run(row("S1", "fall", " low income ", "yes", "70", "68", "0.95", "0"));
        assertEquals(1, r.acceptedRows());
        Observation o = r.accepted().get(0);
        assertEquals(EconomicStatus.LOW_INCOME, o.economicStatus());
        assertTrue(o.englishLearner());
    }

    @Test
    void rawStudentIdIsNeverKept() {
        ImportResult r = run(row("S1", "FALL", "LOW_INCOME", "N", "70", "68", "0.95", "0"));
        assertNotEquals("S1", r.accepted().get(0).studentKey());
    }

    @Test
    void issuesCarryTheSpreadsheetRowNumber() {
        ImportResult r = run(
                row("S1", "FALL", "LOW_INCOME", "N", "70", "68", "0.95", "0"),
                row("S2", "FALL", "LOW_INCOME", "N", "70", "68", "0.95", "0"),
                row("S3", "Winter", "LOW_INCOME", "N", "70", "68", "0.95", "0"));
        // header is row 1, so the third data row is row 4
        assertEquals(4, r.issues().get(0).rowNumber());
    }

    @Test
    void rowWithTheWrongNumberOfColumnsIsRejected() {
        ImportResult r = processor.process(HEADER + "\nS1,Maple Grove,5\n");
        assertEquals(1, r.rejectedRows());
        assertTrue(hasCode(r, "MALFORMED_ROW"));
    }

    @Test
    void headerMatchingIsCaseInsensitiveAndOrderIndependent() {
        String csv = "SCHOOL,Student_ID,grade,academic_year,term,economic_status,english_learner,math_score,"
                + "reading_score,attendance_rate,discipline_incidents\n"
                + "Maple Grove,S1,5,2022,FALL,LOW_INCOME,N,70,68,0.95,0\n";
        ImportResult r = processor.process(csv);
        assertEquals(1, r.acceptedRows());
        assertEquals("Maple Grove", r.accepted().get(0).school());
    }

    @Test
    void issueCountsSummarizeByCode() {
        ImportResult r = run(
                row("S1", "FALL", "LOW_INCOME", "N", "", "68", "0.95", "0"),
                row("S2", "FALL", "LOW_INCOME", "N", "", "68", "0.95", "0"));
        assertEquals(List.of("MISSING_MATH_SCORE"), List.copyOf(r.issueCounts().keySet()));
        assertEquals(2L, r.issueCounts().get("MISSING_MATH_SCORE").longValue());
    }
}

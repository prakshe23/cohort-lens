package com.cohortlens.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvExporterTest {

    @Test
    void writesHeaderAndOneLinePerObservation() {
        Observation o = new Observation("abc123", "Maple Grove", 5, 2022, Term.FALL, EconomicStatus.LOW_INCOME,
                true, 70.5, null, 0.95, 2);
        String[] lines = CsvExporter.toCsv(List.of(o)).split("\n");
        assertEquals(CsvExporter.HEADER, lines[0]);
        assertEquals("abc123,Maple Grove,5,2022,FALL,LOW_INCOME,Y,70.5,,0.95,2", lines[1]);
    }

    @Test
    void quotesFieldsThatContainCommas() {
        assertEquals("\"Lincoln, East\"", CsvExporter.field("Lincoln, East"));
    }

    @Test
    void neutralizesSpreadsheetFormulas() {
        assertTrue(CsvExporter.field("=HYPERLINK(\"http://x\")").startsWith("\"'="));
        assertFalse(CsvExporter.field("Maple Grove").startsWith("'"));
    }
}

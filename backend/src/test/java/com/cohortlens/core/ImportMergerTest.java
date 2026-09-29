package com.cohortlens.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ImportMergerTest {
    private static final String HEADER = String.join(",", ImportProcessor.REQUIRED_COLUMNS);
    private final ImportProcessor processor = new ImportProcessor(new Pseudonymizer("a-test-secret-with-enough-length"));

    private ImportResult load() {
        return processor.process(HEADER + "\n"
                + "S1,Maple Grove,5,2022,FALL,LOW_INCOME,N,70,68,0.95,0\n"
                + "S2,Maple Grove,5,2022,FALL,LOW_INCOME,N,71,69,0.94,0\n");
    }

    @Test
    void rowsAlreadyStoredAreRejectedWithTheirOriginalRowNumber() {
        ImportResult loaded = load();
        Set<String> existing = Set.of(loaded.accepted().get(1).naturalKey()); // S2 is already stored
        ImportResult merged = ImportMerger.rejectExisting(loaded, existing);
        assertEquals(1, merged.acceptedRows());
        assertEquals(1, merged.rejectedRows());
        ValidationIssue issue = merged.issues().get(0);
        assertEquals("DUPLICATE_EXISTING", issue.code());
        assertEquals(3, issue.rowNumber()); // header is row 1, S2 is the second data row
    }

    @Test
    void nothingChangesWhenNothingIsStored() {
        ImportResult loaded = load();
        ImportResult merged = ImportMerger.rejectExisting(loaded, Set.of());
        assertEquals(2, merged.acceptedRows());
        assertEquals(0, merged.rejectedRows());
    }

    @Test
    void fatalResultsPassThrough() {
        ImportResult fatal = processor.process("a,b\n1,2\n");
        assertTrue(ImportMerger.rejectExisting(fatal, Set.of("x")).fatal());
    }
}

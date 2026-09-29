package com.cohortlens.app.web;

import com.cohortlens.app.service.AuditService;
import com.cohortlens.app.service.ObservationStore;
import com.cohortlens.core.CsvExporter;
import com.cohortlens.core.Observation;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exports the cleaned, pseudonymized data. Every export is written to the audit log. */
@RestController
public class ExportController {
    private final ObservationStore store;
    private final AuditService audit;

    public ExportController(ObservationStore store, AuditService audit) {
        this.store = store;
        this.audit = audit;
    }

    @GetMapping(path = "/api/export.csv", produces = "text/csv")
    public ResponseEntity<String> export(Authentication authentication) {
        List<Observation> all = store.all();
        audit.record(authentication.getName(), "EXPORT", "observations", all.size() + " rows exported");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cohortlens_export.csv\"")
                .body(CsvExporter.toCsv(all));
    }
}

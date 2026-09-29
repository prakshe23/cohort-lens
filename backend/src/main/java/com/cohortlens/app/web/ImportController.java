package com.cohortlens.app.web;

import com.cohortlens.app.service.ImportService;
import com.cohortlens.core.ImportSummary;
import com.cohortlens.core.ValidationIssue;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Upload a CSV as the raw request body (Content-Type: text/csv). Example:
 * curl -u admin:PASSWORD -H "Content-Type: text/csv" --data-binary @data.csv "http://localhost:8080/api/imports?filename=data.csv&amp;mode=APPEND"
 */
@RestController
@RequestMapping("/api/imports")
public class ImportController {
    private final ImportService service;
    private final long maxUploadBytes;

    public ImportController(ImportService service, @Value("${cohortlens.max-upload-bytes}") long maxUploadBytes) {
        this.service = service;
        this.maxUploadBytes = maxUploadBytes;
    }

    @PostMapping(consumes = "text/csv")
    public ResponseEntity<?> upload(@RequestBody byte[] body,
                                    @RequestParam(defaultValue = "upload.csv") String filename,
                                    @RequestParam(defaultValue = "APPEND") String mode,
                                    Authentication authentication) {
        String normalizedMode = mode.toUpperCase(Locale.ROOT);
        if (!normalizedMode.equals("APPEND") && !normalizedMode.equals("REPLACE")) {
            throw new IllegalArgumentException("mode must be APPEND or REPLACE");
        }
        if (body.length > maxUploadBytes) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(java.util.Map.of("error", "File is larger than " + maxUploadBytes + " bytes"));
        }
        String csv = new String(body, StandardCharsets.UTF_8);
        ImportSummary summary = service.runImport(filename, normalizedMode, csv, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @GetMapping
    public List<ImportSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}/issues")
    public List<ValidationIssue> issues(@PathVariable long id,
                                        @RequestParam(required = false) String severity,
                                        @RequestParam(required = false) String code,
                                        @RequestParam(defaultValue = "200") int limit) {
        return service.issues(id, severity, code, limit);
    }
}

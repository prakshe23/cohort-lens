package com.cohortlens.app.persistence;

import com.cohortlens.core.ImportResult;
import com.cohortlens.core.ImportSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "import_jobs")
public class ImportJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "import_job_seq")
    @SequenceGenerator(name = "import_job_seq", sequenceName = "import_job_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(name = "import_mode", nullable = false)
    private String importMode;

    @Column(nullable = false)
    private String status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "accepted_rows", nullable = false)
    private int acceptedRows;

    @Column(name = "rejected_rows", nullable = false)
    private int rejectedRows;

    @Column(nullable = false)
    private long warnings;

    @Column(nullable = false)
    private long errors;

    @Column(name = "issue_counts", nullable = false, length = 4000)
    private String issueCounts;

    @Column(length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    protected ImportJobEntity() {
        // required by JPA
    }

    public ImportJobEntity(String filename, String mode, ImportResult r, ObjectMapper mapper, String createdBy) {
        this.filename = filename.length() > 255 ? filename.substring(0, 255) : filename;
        this.importMode = mode;
        this.status = r.fatal() ? "FAILED" : "COMPLETED";
        this.totalRows = r.totalRows();
        this.acceptedRows = r.acceptedRows();
        this.rejectedRows = r.rejectedRows();
        this.warnings = r.warningCount();
        this.errors = r.errorCount();
        try {
            this.issueCounts = mapper.writeValueAsString(r.issueCounts());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        this.message = r.fatal() ? r.fatalMessage() : null;
        this.createdAt = Instant.now();
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public ImportSummary toSummary(ObjectMapper mapper) {
        Map<String, Long> counts;
        try {
            counts = mapper.readValue(issueCounts, new TypeReference<Map<String, Long>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return new ImportSummary(id, filename, importMode, status, totalRows, acceptedRows, rejectedRows,
                warnings, errors, counts, message, createdAt.toString(), createdBy);
    }
}

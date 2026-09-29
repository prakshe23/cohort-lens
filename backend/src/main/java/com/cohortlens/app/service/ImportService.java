package com.cohortlens.app.service;

import com.cohortlens.app.persistence.ImportJobEntity;
import com.cohortlens.app.persistence.ImportJobRepository;
import com.cohortlens.app.persistence.ObservationEntity;
import com.cohortlens.app.persistence.ObservationRepository;
import com.cohortlens.app.persistence.ValidationIssueEntity;
import com.cohortlens.app.persistence.ValidationIssueRepository;
import com.cohortlens.core.ImportMerger;
import com.cohortlens.core.ImportProcessor;
import com.cohortlens.core.ImportResult;
import com.cohortlens.core.ImportSummary;
import com.cohortlens.core.Severity;
import com.cohortlens.core.ValidationIssue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ImportService {
    /** Stored issues per import are capped so one bad file cannot fill the database. */
    static final int MAX_STORED_ISSUES = 10_000;

    private final ImportProcessor processor;
    private final ObservationRepository observations;
    private final ImportJobRepository jobs;
    private final ValidationIssueRepository issues;
    private final ObservationStore store;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public ImportService(ImportProcessor processor, ObservationRepository observations, ImportJobRepository jobs,
                         ValidationIssueRepository issues, ObservationStore store, AuditService audit,
                         ObjectMapper mapper) {
        this.processor = processor;
        this.observations = observations;
        this.jobs = jobs;
        this.issues = issues;
        this.store = store;
        this.audit = audit;
        this.mapper = mapper;
    }

    /**
     * Validates the CSV and stores the accepted rows. APPEND rejects rows whose student and term are already
     * stored. REPLACE removes all stored observations first. Everything happens in one transaction, so a
     * failure leaves the previous data untouched.
     */
    @Transactional
    public ImportSummary runImport(String filename, String mode, String csv, String actor) {
        ImportResult result = processor.process(csv);
        if (mode.equals("APPEND")) {
            result = ImportMerger.rejectExisting(result, new HashSet<>(observations.findAllNaturalKeys()));
        }

        ImportJobEntity job = jobs.save(new ImportJobEntity(filename, mode, result, mapper, actor));
        Long jobId = job.getId();

        if (!result.fatal()) {
            if (mode.equals("REPLACE")) {
                long removed = observations.count();
                observations.deleteAllInBatch();
                audit.record(actor, "DELETE_ALL", "observations",
                        removed + " rows removed by replace import " + jobId);
            }
            observations.saveAll(result.accepted().stream().map(o -> new ObservationEntity(o, jobId)).toList());

            List<ValidationIssue> toStore = result.issues().size() > MAX_STORED_ISSUES
                    ? result.issues().subList(0, MAX_STORED_ISSUES) : result.issues();
            issues.saveAll(toStore.stream().map(i -> new ValidationIssueEntity(i, jobId)).toList());
        }

        audit.record(actor, "IMPORT", "import " + jobId, filename + " (" + mode + "): "
                + result.acceptedRows() + " accepted, " + result.rejectedRows() + " rejected"
                + (result.fatal() ? ", FAILED: " + result.fatalMessage() : ""));

        // Drop the analytics cache only once the new data is committed.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                store.invalidate();
            }
        });
        return job.toSummary(mapper);
    }

    @Transactional(readOnly = true)
    public List<ImportSummary> list() {
        return jobs.findTop100ByOrderByIdDesc().stream().map(j -> j.toSummary(mapper)).toList();
    }

    @Transactional(readOnly = true)
    public List<ValidationIssue> issues(long importId, String severity, String code, int limit) {
        if (!jobs.existsById(importId)) {
            throw new NotFoundException("import not found");
        }
        return issues.findByImportJobIdOrderBySourceRowAscIdAsc(importId).stream()
                .map(ValidationIssueEntity::toRecord)
                .filter(i -> severity == null || severity.isBlank() || i.severity() == Severity.valueOf(severity.toUpperCase(java.util.Locale.ROOT)))
                .filter(i -> code == null || code.isBlank() || i.code().equals(code))
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}

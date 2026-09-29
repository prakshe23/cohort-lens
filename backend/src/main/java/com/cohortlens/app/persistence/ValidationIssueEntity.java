package com.cohortlens.app.persistence;

import com.cohortlens.core.Severity;
import com.cohortlens.core.ValidationIssue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "validation_issues")
public class ValidationIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "validation_issue_seq")
    @SequenceGenerator(name = "validation_issue_seq", sequenceName = "validation_issue_seq", allocationSize = 50)
    private Long id;

    @Column(name = "import_job_id", nullable = false)
    private Long importJobId;

    @Column(name = "source_row", nullable = false)
    private int sourceRow;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "raw_value", nullable = false, length = 100)
    private String rawValue;

    protected ValidationIssueEntity() {
        // required by JPA
    }

    public ValidationIssueEntity(ValidationIssue issue, Long importJobId) {
        this.importJobId = importJobId;
        this.sourceRow = issue.rowNumber();
        this.fieldName = issue.field();
        this.severity = issue.severity();
        this.code = issue.code();
        this.message = issue.message().length() > 500 ? issue.message().substring(0, 500) : issue.message();
        this.rawValue = issue.rawValue() == null ? "" : issue.rawValue();
    }

    public ValidationIssue toRecord() {
        return new ValidationIssue(sourceRow, fieldName, severity, code, message, rawValue);
    }
}

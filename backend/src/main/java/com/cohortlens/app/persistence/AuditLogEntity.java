package com.cohortlens.app.persistence;

import com.cohortlens.core.AuditEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_log_seq")
    @SequenceGenerator(name = "audit_log_seq", sequenceName = "audit_log_seq", allocationSize = 1)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(nullable = false, length = 2000)
    private String detail;

    protected AuditLogEntity() {
        // required by JPA
    }

    public AuditLogEntity(String actor, String action, String entityType, String detail) {
        this.occurredAt = Instant.now();
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.detail = detail.length() > 2000 ? detail.substring(0, 2000) : detail;
    }

    public AuditEntry toRecord() {
        return new AuditEntry(id, occurredAt.toString(), actor, action, entityType, detail);
    }
}

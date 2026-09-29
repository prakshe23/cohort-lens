package com.cohortlens.app.service;

import com.cohortlens.app.persistence.AuditLogEntity;
import com.cohortlens.app.persistence.AuditLogRepository;
import com.cohortlens.core.AuditEntry;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String actor, String action, String entityType, String detail) {
        repository.save(new AuditLogEntity(actor, action, entityType, detail));
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> latest() {
        return repository.findTop200ByOrderByIdDesc().stream().map(AuditLogEntity::toRecord).toList();
    }
}

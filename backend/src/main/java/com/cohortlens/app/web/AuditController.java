package com.cohortlens.app.web;

import com.cohortlens.app.service.AuditService;
import com.cohortlens.core.AuditEntry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/api/audit")
    public List<AuditEntry> latest() {
        return audit.latest();
    }
}

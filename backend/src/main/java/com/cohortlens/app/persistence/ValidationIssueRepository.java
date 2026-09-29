package com.cohortlens.app.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValidationIssueRepository extends JpaRepository<ValidationIssueEntity, Long> {
    List<ValidationIssueEntity> findByImportJobIdOrderBySourceRowAscIdAsc(Long importJobId);
}

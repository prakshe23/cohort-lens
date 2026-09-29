package com.cohortlens.app.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJobEntity, Long> {
    List<ImportJobEntity> findTop100ByOrderByIdDesc();
}

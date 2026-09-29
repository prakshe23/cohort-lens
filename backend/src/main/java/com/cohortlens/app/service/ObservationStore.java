package com.cohortlens.app.service;

import com.cohortlens.app.persistence.ObservationEntity;
import com.cohortlens.app.persistence.ObservationRepository;
import com.cohortlens.core.Observation;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side for analytics. The dataset is small enough (tens of thousands of rows) to keep in memory,
 * so the whole list is cached and rebuilt only after an import changes the data.
 */
@Service
public class ObservationStore {
    private final ObservationRepository repository;
    private volatile List<Observation> cache;

    public ObservationStore(ObservationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Observation> all() {
        List<Observation> current = cache;
        if (current == null) {
            current = repository.findAll().stream().map(ObservationEntity::toRecord).toList();
            cache = current;
        }
        return current;
    }

    public void invalidate() {
        cache = null;
    }
}

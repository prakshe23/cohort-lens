package com.cohortlens.app.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ObservationRepository extends JpaRepository<ObservationEntity, Long> {

    /** Keys shaped like Observation.naturalKey(): student key, a bar, then the term index. */
    @Query("select concat(o.studentKey, '|', cast(o.termIndex as String)) from ObservationEntity o")
    List<String> findAllNaturalKeys();
}

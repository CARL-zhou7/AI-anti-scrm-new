package com.aiscam.antiscam.content.domain;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface FraudScenarioRepository extends Repository<FraudScenario, Long> {
    List<FraudScenario> findAllByOrderByIdAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FraudScenario s where s.id = :id")
    Optional<FraudScenario> lockById(@Param("id") long id);
}

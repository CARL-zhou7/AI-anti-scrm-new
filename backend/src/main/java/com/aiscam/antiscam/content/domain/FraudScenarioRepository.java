package com.aiscam.antiscam.content.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface FraudScenarioRepository extends Repository<FraudScenario, Long> {
    List<FraudScenario> findAllByOrderByIdAsc();
}

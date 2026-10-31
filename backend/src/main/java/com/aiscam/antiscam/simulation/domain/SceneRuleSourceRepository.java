package com.aiscam.antiscam.simulation.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SceneRuleSourceRepository extends JpaRepository<SceneRuleSource, SceneRuleSourceId> {
    List<SceneRuleSource> findByRuleIdOrderByInterventionIdAsc(long ruleId);

    boolean existsByInterventionId(long interventionId);

    Optional<SceneRuleSource> findByInterventionId(long interventionId);

    long countByRuleId(long ruleId);
}

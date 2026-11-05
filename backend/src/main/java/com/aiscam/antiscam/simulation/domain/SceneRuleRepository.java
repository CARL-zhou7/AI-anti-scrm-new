package com.aiscam.antiscam.simulation.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SceneRuleRepository extends JpaRepository<SceneRule, Long> {
    /** Fixed snapshot order: newest version first, then id; conflict handling stays manual. */
    List<SceneRule> findByScenarioCodeAndStatusOrderByVersionNoDescIdAsc(String scenarioCode, SceneRuleStatus status);

    List<SceneRule> findByScenarioCodeOrderByVersionNoDescIdAsc(String scenarioCode);

    @Query("select count(r) > 0 from SceneRule r where r.scenarioCode=:scenarioCode and r.title=:title and r.targetRole=com.aiscam.antiscam.simulation.domain.SpeakerRole.SCAMMER")
    boolean existsByScenarioCodeAndTitle(@Param("scenarioCode") String scenarioCode, @Param("title") String title);

    @Query("select count(r) > 0 from SceneRule r where r.scenarioCode=:scenarioCode and r.title=:title and r.id<>:id and r.targetRole=com.aiscam.antiscam.simulation.domain.SpeakerRole.SCAMMER")
    boolean existsByScenarioCodeAndTitleAndIdNot(@Param("scenarioCode") String scenarioCode, @Param("title") String title, @Param("id") long id);

    boolean existsByScenarioCodeAndTargetRoleAndTitle(String scenarioCode, SpeakerRole targetRole, String title);

    boolean existsByScenarioCodeAndTargetRoleAndTitleAndIdNot(String scenarioCode, SpeakerRole targetRole, String title, long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SceneRule r where r.id = :id")
    Optional<SceneRule> lockById(@Param("id") long id);
}

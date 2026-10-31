package com.aiscam.antiscam.simulation.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SimulationScammerDraftRepository extends JpaRepository<SimulationScammerDraft, Long> {
    List<SimulationScammerDraft> findBySessionIdOrderByRoundNoAscRevisionNoAsc(long sessionId);
    Optional<SimulationScammerDraft> findFirstByRoundIdOrderByRevisionNoAsc(long roundId);

    Optional<SimulationScammerDraft> findFirstByRoundIdAndStatusOrderByRevisionNoDesc(long roundId, ScammerDraftStatus status);

    @Query("select d from SimulationScammerDraft d where d.roundId = :roundId and d.status = com.aiscam.antiscam.simulation.domain.ScammerDraftStatus.DRAFT")
    Optional<SimulationScammerDraft> findActiveByRoundId(@Param("roundId") long roundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SimulationScammerDraft d where d.id = :id")
    Optional<SimulationScammerDraft> lockById(@Param("id") long id);

    @Query("select coalesce(max(d.revisionNo), -1) from SimulationScammerDraft d where d.roundId = :roundId")
    int maxRevision(@Param("roundId") long roundId);
}

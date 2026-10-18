package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SimulationSessionRepository extends JpaRepository<SimulationSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SimulationSession s where s.id = :id")
    Optional<SimulationSession> lockById(@Param("id") long id);

    Optional<SimulationSession> findByUserIdAndStartRequestId(long userId, String startRequestId);

    Optional<SimulationSession> findFirstByUserIdAndStatus(long userId, SimulationStatus status);

    List<SimulationSession> findByStatus(SimulationStatus status);

    @Query("select s.id from SimulationSession s where s.status = com.aiscam.antiscam.simulation.domain.SimulationStatus.RUNNING"
            + " and (s.deadlineAt <= :now or s.pausedAt is not null)")
    List<Long> findRunningDueOrPaused(@Param("now") Instant now);

    @Query(value = "select count(*) from simulation_session where user_id = :userId and ("
            + "json_unquote(json_extract(scammer_snapshot, '$.avatar_key')) = :key"
            + " or json_unquote(json_extract(scammer_snapshot, '$.speaking_avatar_key')) = :key"
            + " or json_unquote(json_extract(victim_snapshot, '$.avatar_key')) = :key"
            + " or json_unquote(json_extract(victim_snapshot, '$.speaking_avatar_key')) = :key)", nativeQuery = true)
    long countAvatarReferences(@Param("userId") long userId, @Param("key") String key);
}

package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface SimulationSessionRepository extends JpaRepository<SimulationSession, Long> {
    List<SimulationSession> findByUserIdOrderByStartedAtDescIdDesc(long userId, Pageable page);

    @Query("select s from SimulationSession s where s.userId = :userId"
            + " and (s.startedAt < :startedAt or (s.startedAt = :startedAt and s.id < :id))"
            + " order by s.startedAt desc, s.id desc")
    List<SimulationSession> findPageBefore(@Param("userId") long userId, @Param("startedAt") Instant startedAt,
            @Param("id") long id, Pageable page);

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

package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface SimulationReportRepository extends JpaRepository<SimulationReport, Long> {
    List<SimulationReport> findBySessionIdIn(Collection<Long> sessionIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SimulationReport r where r.sessionId = :sessionId")
    Optional<SimulationReport> lockById(long sessionId);

    List<SimulationReport> findTop5ByStatusOrderByQueuedAtAscSessionIdAsc(ReportStatus status);
    List<SimulationReport> findByStatus(ReportStatus status);
    List<SimulationReport> findTop5ByStatusAndAttemptDeadlineAtBeforeOrderByAttemptDeadlineAtAsc(ReportStatus status, Instant at);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SimulationReport r set r.status = 'SUCCEEDED', r.contentJson = :contentJson,"
            + " r.errorCode = null, r.finishedAt = :now, r.updatedAt = :now"
            + " where r.sessionId = :sessionId and r.status = 'RUNNING' and r.attemptNo = :attemptNo"
            + " and r.taskToken = :taskToken and r.attemptDeadlineAt >= :now")
    int markSucceeded(long sessionId, int attemptNo, String taskToken, String contentJson, Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SimulationReport r set r.status = 'FAILED', r.contentJson = null,"
            + " r.errorCode = :errorCode, r.finishedAt = :now, r.updatedAt = :now"
            + " where r.sessionId = :sessionId and r.status = 'RUNNING' and r.attemptNo = :attemptNo"
            + " and r.taskToken = :taskToken and r.attemptDeadlineAt >= :now")
    int markFailed(long sessionId, int attemptNo, String taskToken, String errorCode, Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SimulationReport r set r.status = 'FAILED', r.contentJson = null,"
            + " r.errorCode = 'TIMEOUT', r.finishedAt = :now, r.updatedAt = :now"
            + " where r.sessionId = :sessionId and r.status = 'RUNNING' and r.attemptNo = :attemptNo"
            + " and r.taskToken = :taskToken and r.attemptDeadlineAt < :now")
    int failExpired(long sessionId, int attemptNo, String taskToken, Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SimulationReport r set r.status = 'FAILED', r.contentJson = null,"
            + " r.errorCode = 'SERVICE_RESTARTED', r.finishedAt = :now, r.updatedAt = :now"
            + " where r.sessionId = :sessionId and r.status = 'RUNNING' and r.attemptNo = :attemptNo"
            + " and r.taskToken = :taskToken")
    int failRunning(long sessionId, int attemptNo, String taskToken, Instant now);
}

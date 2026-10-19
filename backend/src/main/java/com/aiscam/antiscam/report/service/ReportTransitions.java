package com.aiscam.antiscam.report.service;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.service.SimulationStateAssembler;
import com.aiscam.antiscam.sse.SimulationEventHub;
import com.aiscam.antiscam.sse.SimulationEventHub.SimulationEvent;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ReportTransitions {
    private static final Logger log = LoggerFactory.getLogger(ReportTransitions.class);
    private final SimulationReportRepository reports;
    private final SimulationSessionRepository sessions;
    private final SimulationStateAssembler assembler;
    private final SimulationEventHub hub;
    private final SimulationProperties properties;

    public ReportTransitions(SimulationReportRepository reports, SimulationSessionRepository sessions,
            SimulationStateAssembler assembler, SimulationEventHub hub, SimulationProperties properties) {
        this.reports = reports;
        this.sessions = sessions;
        this.assembler = assembler;
        this.hub = hub;
        this.properties = properties;
    }

    public record Claimed(long sessionId, int attemptNo, String taskToken, Instant startedAt, Instant attemptDeadlineAt) {}

    public Claimed claim(long sessionId, Instant now) {
        var row = reports.lockById(sessionId).orElse(null);
        if (row == null || row.getStatus() != ReportStatus.PENDING) return null;
        var session = sessions.findById(sessionId).orElseThrow();
        if (session.getStatus() != SimulationStatus.FINISHED) return null;
        row.claim(UUID.randomUUID().toString(), now, now.plusMillis(properties.getReportAttemptTimeoutMs()));
        reports.flush();
        publish(session);
        return new Claimed(sessionId, row.getAttemptNo(), row.getTaskToken(), row.getStartedAt(), row.getAttemptDeadlineAt());
    }

    public boolean markSucceeded(Claimed attempt, String contentJson, Instant now) {
        int changed = reports.markSucceeded(attempt.sessionId(), attempt.attemptNo(), attempt.taskToken(), contentJson, now);
        return publishIfChanged(attempt.sessionId(), changed);
    }

    public boolean markFailed(Claimed attempt, ReportErrorCode code, Instant now) {
        int changed = reports.markFailed(attempt.sessionId(), attempt.attemptNo(), attempt.taskToken(), code.name(), now);
        return publishIfChanged(attempt.sessionId(), changed);
    }

    public int failExpired(Instant now) {
        int count = 0;
        for (var row : reports.findTop5ByStatusAndAttemptDeadlineAtBeforeOrderByAttemptDeadlineAtAsc(ReportStatus.RUNNING, now)) {
            int changed = reports.failExpired(row.getSessionId(), row.getAttemptNo(), row.getTaskToken(), now);
            if (publishIfChanged(row.getSessionId(), changed)) count++;
        }
        return count;
    }

    public int failRunningOnRestart(Instant now) {
        int count = 0;
        for (var row : reports.findByStatus(ReportStatus.RUNNING)) {
            int changed = reports.failRunning(row.getSessionId(), row.getAttemptNo(), row.getTaskToken(), now);
            if (publishIfChanged(row.getSessionId(), changed)) count++;
        }
        return count;
    }

    public boolean repairMissing(long sessionId, Instant now) {
        // Recovery follows the existing parent -> report lock order; normal report work never locks the parent.
        var session = sessions.lockById(sessionId).orElse(null);
        if (session == null || session.getStatus() != SimulationStatus.FINISHED || reports.existsById(sessionId)) return false;
        if (session.getOutcomeCode() == null || session.getOutcomeReason() == null || session.getEndedAt() == null) {
            log.warn("report recovery skipped inconsistent sessionId={}", sessionId);
            return false;
        }
        reports.saveAndFlush(SimulationReport.pending(sessionId, now));
        log.warn("report recovery repaired missing row sessionId={}", sessionId);
        publish(session);
        return true;
    }

    public ReportRetryResult retry(SimulationSession session, Instant now) {
        var row = reports.lockById(session.getId()).orElse(null);
        var action = ReportRetryRules.decide(session.getStatus(), row == null ? null : row.getStatus());
        switch (action) {
            case NOT_APPLICABLE -> throw ApiException.reportNotApplicable();
            case INCONSISTENT -> throw ApiException.dataInconsistent("记录状态异常，请稍后重试");
            case REQUEUE -> {
                row.requeue(now);
                reports.flush();
                publish(session);
                return new ReportRetryResult(202, assembler.reportView(session));
            }
            default -> { return new ReportRetryResult(200, assembler.reportView(session)); }
        }
    }

    private boolean publishIfChanged(long sessionId, int changed) {
        var session = sessions.findById(sessionId).orElseThrow();
        if (changed != 1) {
            // Read the current row on an ambiguous/late completion; never replace it with this attempt's output.
            reports.findById(sessionId);
            return false;
        }
        publish(session);
        return true;
    }

    private void publish(SimulationSession session) {
        hub.publishAfterCommit(new SimulationEvent("report.updated", session.getId(), session.getStateVersion(), assembler.reportView(session)));
    }
}

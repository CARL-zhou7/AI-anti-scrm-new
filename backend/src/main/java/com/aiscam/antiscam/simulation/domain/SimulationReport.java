package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Report row; this phase only creates it as PENDING in the same transaction as the formal finish. */
@Entity
@Table(name = "simulation_report")
public class SimulationReport {
    @Id @Column(name = "session_id")
    private Long sessionId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16)
    private ReportStatus status;
    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;
    @Column(name = "task_token", columnDefinition = "char(36)")
    private String taskToken;
    @Column(name = "queued_at", nullable = false, columnDefinition = "datetime(3)")
    private Instant queuedAt;
    @Column(name = "started_at", columnDefinition = "datetime(3)")
    private Instant startedAt;
    @Column(name = "attempt_deadline_at", columnDefinition = "datetime(3)")
    private Instant attemptDeadlineAt;
    @Column(name = "finished_at", columnDefinition = "datetime(3)")
    private Instant finishedAt;
    @Column(name = "content_schema_version", nullable = false, columnDefinition = "smallint unsigned")
    private Integer contentSchemaVersion;
    @Column(name = "content_json", columnDefinition = "json")
    private String contentJson;
    @Column(name = "error_code", length = 32)
    private String errorCode;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(3)")
    private Instant updatedAt;

    protected SimulationReport() {}

    public static SimulationReport pending(long sessionId, Instant now) {
        var report = new SimulationReport();
        report.sessionId = sessionId;
        report.status = ReportStatus.PENDING;
        report.attemptNo = 0;
        report.queuedAt = now;
        report.contentSchemaVersion = 1;
        report.updatedAt = now;
        return report;
    }

    public long getSessionId() { return sessionId; }
    public ReportStatus getStatus() { return status; }
    public int getAttemptNo() { return attemptNo; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getErrorCode() { return errorCode; }
}

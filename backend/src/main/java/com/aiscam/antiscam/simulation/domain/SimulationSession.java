package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One simulation run. Mutators are only called from {@code SimulationTransitions} while the row is locked; the
 * {@code stateVersion} is the application-level gate against late results (deliberately not JPA {@code @Version}).
 */
@Entity
@Table(name = "simulation_session")
public class SimulationSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "start_request_id", nullable = false, columnDefinition = "char(36)")
    private String startRequestId;
    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;
    @Column(name = "scammer_profile_id", nullable = false)
    private Long scammerProfileId;
    @Column(name = "victim_profile_id", nullable = false)
    private Long victimProfileId;
    @Column(name = "snapshot_schema_version", nullable = false, columnDefinition = "smallint unsigned")
    private Integer snapshotSchemaVersion;
    @Column(name = "scenario_snapshot", nullable = false, columnDefinition = "json")
    private String scenarioSnapshot;
    @Column(name = "scammer_snapshot", nullable = false, columnDefinition = "json")
    private String scammerSnapshot;
    @Column(name = "victim_snapshot", nullable = false, columnDefinition = "json")
    private String victimSnapshot;
    @Column(name = "scammer_prompt_id", nullable = false)
    private Long scammerPromptId;
    @Column(name = "victim_prompt_id", nullable = false)
    private Long victimPromptId;
    @Column(name = "narrator_prompt_id", nullable = false)
    private Long narratorPromptId;
    @Column(name = "report_prompt_id", nullable = false)
    private Long reportPromptId;
    @Column(name = "runtime_snapshot", nullable = false, columnDefinition = "json")
    private String runtimeSnapshot;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16)
    private SimulationStatus status;
    @Column(name = "active_user_id", insertable = false, updatable = false)
    private Long activeUserId;
    @Column(name = "current_round_no", nullable = false, columnDefinition = "tinyint unsigned")
    private Integer currentRoundNo;
    @Column(name = "state_version", nullable = false)
    private Long stateVersion;
    @Column(name = "started_at", nullable = false, columnDefinition = "datetime(3)")
    private Instant startedAt;
    @Column(name = "received_at", nullable = false, columnDefinition = "datetime(3)")
    private Instant receivedAt;
    @Column(name = "timing_evidence", nullable = false, columnDefinition = "json")
    private String timingEvidence;
    @Column(name = "deadline_at", nullable = false, columnDefinition = "datetime(3)")
    private Instant deadlineAt;
    @Column(name = "paused_at", columnDefinition = "datetime(3)")
    private Instant pausedAt;
    @Column(name = "paused_total_ms", nullable = false)
    private Integer pausedTotalMs;
    @Column(name = "ended_at", columnDefinition = "datetime(3)")
    private Instant endedAt;
    @Enumerated(EnumType.STRING) @Column(name = "outcome_code", length = 24)
    private OutcomeCode outcomeCode;
    @Column(name = "outcome_reason", columnDefinition = "text")
    private String outcomeReason;
    @Enumerated(EnumType.STRING) @Column(name = "limit_reason", length = 16)
    private LimitReason limitReason;
    @Enumerated(EnumType.STRING) @Column(name = "interruption_code", length = 32)
    private InterruptionCode interruptionCode;
    @Column(name = "interruption_detail", length = 255)
    private String interruptionDetail;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant updatedAt;

    protected SimulationSession() {}

    public SimulationSession(long userId, String startRequestId, long scenarioId, long scammerProfileId,
            long victimProfileId, String scenarioSnapshot, String scammerSnapshot, String victimSnapshot,
            long scammerPromptId, long victimPromptId, long narratorPromptId, long reportPromptId,
            String runtimeSnapshot, Instant startedAt, Instant receivedAt, String timingEvidence) {
        this.userId = userId;
        this.startRequestId = startRequestId;
        this.scenarioId = scenarioId;
        this.scammerProfileId = scammerProfileId;
        this.victimProfileId = victimProfileId;
        this.snapshotSchemaVersion = 1;
        this.scenarioSnapshot = scenarioSnapshot;
        this.scammerSnapshot = scammerSnapshot;
        this.victimSnapshot = victimSnapshot;
        this.scammerPromptId = scammerPromptId;
        this.victimPromptId = victimPromptId;
        this.narratorPromptId = narratorPromptId;
        this.reportPromptId = reportPromptId;
        this.runtimeSnapshot = runtimeSnapshot;
        this.status = SimulationStatus.RUNNING;
        this.currentRoundNo = 0;
        this.stateVersion = 0L;
        this.startedAt = startedAt;
        this.receivedAt = receivedAt;
        this.timingEvidence = timingEvidence;
        this.deadlineAt = startedAt.plusMillis(600_000);
        this.pausedTotalMs = 0;
    }

    public Long getId() { return id; }
    public long getUserId() { return userId; }
    public String getStartRequestId() { return startRequestId; }
    public long getScenarioId() { return scenarioId; }
    public long getScammerProfileId() { return scammerProfileId; }
    public long getVictimProfileId() { return victimProfileId; }
    public String getScenarioSnapshot() { return scenarioSnapshot; }
    public String getScammerSnapshot() { return scammerSnapshot; }
    public String getVictimSnapshot() { return victimSnapshot; }
    public long getScammerPromptId() { return scammerPromptId; }
    public long getVictimPromptId() { return victimPromptId; }
    public long getNarratorPromptId() { return narratorPromptId; }
    public long getReportPromptId() { return reportPromptId; }
    public String getRuntimeSnapshot() { return runtimeSnapshot; }
    public SimulationStatus getStatus() { return status; }
    public boolean isRunning() { return status == SimulationStatus.RUNNING; }
    public int getCurrentRoundNo() { return currentRoundNo; }
    public long getStateVersion() { return stateVersion; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public Instant getPausedAt() { return pausedAt; }
    public boolean isPaused() { return pausedAt != null; }
    public int getPausedTotalMs() { return pausedTotalMs; }
    public Instant getEndedAt() { return endedAt; }
    public OutcomeCode getOutcomeCode() { return outcomeCode; }
    public String getOutcomeReason() { return outcomeReason; }
    public LimitReason getLimitReason() { return limitReason; }
    public InterruptionCode getInterruptionCode() { return interruptionCode; }
    public String getInterruptionDetail() { return interruptionDetail; }

    public void bumpVersion() { stateVersion++; }
    public void enterRound(int roundNo) { currentRoundNo = roundNo; bumpVersion(); }
    public void pause(Instant now) { pausedAt = now; bumpVersion(); }

    /** Settles the credited pause time (capped at 300 s per run) into the deadline and clears the pause. */
    public long settlePause(Instant now) {
        if (pausedAt == null) return 0;
        long credit = Math.max(0, Math.min(now.toEpochMilli() - pausedAt.toEpochMilli(), 300_000L - pausedTotalMs));
        pausedTotalMs += (int) credit;
        deadlineAt = deadlineAt.plusMillis(credit);
        pausedAt = null;
        return credit;
    }

    public void finish(OutcomeCode outcome, String reason, LimitReason limit, Instant now) {
        status = SimulationStatus.FINISHED;
        outcomeCode = outcome;
        outcomeReason = reason;
        limitReason = limit;
        endedAt = now;
        bumpVersion();
    }

    public void interrupt(InterruptionCode code, Instant now) {
        status = SimulationStatus.INTERRUPTED;
        interruptionCode = code;
        interruptionDetail = code.message();
        endedAt = now;
        bumpVersion();
    }
}

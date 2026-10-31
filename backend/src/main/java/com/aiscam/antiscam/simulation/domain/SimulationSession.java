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
    @Enumerated(EnumType.STRING) @Column(name = "intervention_mode", nullable = false, length = 16)
    private InterventionMode interventionMode = InterventionMode.NONE;
    @Column(name = "scene_rule_snapshot", columnDefinition = "json")
    private String sceneRuleSnapshot;
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
    @Column(name = "deadline_at", columnDefinition = "datetime(3)")
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
    @Column(name = "winner", length = 16)
    private String winner;
    @Column(name = "final_trust_score", columnDefinition = "tinyint unsigned")
    private Integer finalTrustScore;
    @Enumerated(EnumType.STRING) @Column(name = "limit_reason", length = 16)
    private LimitReason limitReason;
    @Enumerated(EnumType.STRING) @Column(name = "interruption_code", length = 32)
    private InterruptionCode interruptionCode;
    @Column(name = "interruption_detail", length = 255)
    private String interruptionDetail;
    @Column(name = "counterevidence_active", nullable = false)
    private boolean counterevidenceActive;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant updatedAt;

    @Column(name = "initial_available_funds_yuan", nullable = false, columnDefinition = "int unsigned")
    private int initialAvailableFundsYuan;
    @Column(name = "available_balance_yuan", nullable = false, columnDefinition = "int unsigned")
    private int availableBalanceYuan;
    @Column(name = "total_paid_yuan", nullable = false, columnDefinition = "int unsigned")
    private int totalPaidYuan;
    @Column(name = "total_refunded_yuan", nullable = false, columnDefinition = "int unsigned")
    private int totalRefundedYuan;
    @Column(name = "actual_loss_yuan", nullable = false, columnDefinition = "int unsigned")
    private int actualLossYuan;
    @Column(name = "additional_payment_count", nullable = false, columnDefinition = "smallint unsigned")
    private int additionalPaymentCount;
    @Column(name = "reserve_released_yuan", insertable = false, updatable = false, columnDefinition = "int unsigned")
    private int reserveReleasedYuan;

    @Column(name = "event_prompt_id", columnDefinition = "bigint unsigned")
    private Long eventPromptId;
    @Column(name = "minimum_rounds_exception", columnDefinition = "tinyint unsigned")
    private int minimumRoundsException;
    @Column(name = "total_rewarded_yuan", columnDefinition = "int unsigned")
    private int totalRewardedYuan;
    @Column(name = "general_reserve_total_yuan", columnDefinition = "int unsigned")
    private int generalReserveTotalYuan;
    @Column(name = "general_reserve_released_yuan", columnDefinition = "int unsigned")
    private int generalReserveReleasedYuan;
    @Column(name = "emergency_reserve_total_yuan", columnDefinition = "int unsigned")
    private int emergencyReserveTotalYuan;
    @Column(name = "emergency_reserve_released_yuan", columnDefinition = "int unsigned")
    private int emergencyReserveReleasedYuan;
    @Column(name = "initial_trust_score", columnDefinition = "tinyint unsigned")
    private Integer initialTrustScore;
    @Column(name = "current_trust_score", columnDefinition = "tinyint unsigned")
    private Integer currentTrustScore;
    @Column(name = "trust_small_cap_yuan", columnDefinition = "int unsigned")
    private Integer trustSmallCapYuan;
    @Column(name = "trust_threshold_accept", columnDefinition = "smallint unsigned")
    private Integer trustThresholdAccept;
    @Column(name = "trust_threshold_small", columnDefinition = "smallint unsigned")
    private Integer trustThresholdSmall;
    @Column(name = "trust_threshold_large", columnDefinition = "smallint unsigned")
    private Integer trustThresholdLarge;
    @Column(name = "trust_positive_factor", precision = 3, scale = 2)
    private java.math.BigDecimal trustPositiveFactor;
    @Column(name = "trust_negative_factor", precision = 3, scale = 2)
    private java.math.BigDecimal trustNegativeFactor;
    public Long getEventPromptId() { return eventPromptId; }
    public int getMinimumRoundsException() { return minimumRoundsException; }
    public int getTotalRewardedYuan() { return totalRewardedYuan; }
    public int getGeneralReserveTotalYuan() { return generalReserveTotalYuan; }
    public int getGeneralReserveReleasedYuan() { return generalReserveReleasedYuan; }
    public int getEmergencyReserveTotalYuan() { return emergencyReserveTotalYuan; }
    public int getEmergencyReserveReleasedYuan() { return emergencyReserveReleasedYuan; }
    public Integer getInitialTrustScore() { return initialTrustScore; }
    public Integer getCurrentTrustScore() { return currentTrustScore; }
    public Integer getTrustSmallCapYuan() { return trustSmallCapYuan; }
    public Integer getTrustThresholdAccept() { return trustThresholdAccept; }
    public Integer getTrustThresholdSmall() { return trustThresholdSmall; }
    public Integer getTrustThresholdLarge() { return trustThresholdLarge; }
    public java.math.BigDecimal getTrustPositiveFactor() { return trustPositiveFactor; }
    public java.math.BigDecimal getTrustNegativeFactor() { return trustNegativeFactor; }

    protected SimulationSession() {}

    public SimulationSession(long userId, String startRequestId, long scenarioId, long scammerProfileId,
            long victimProfileId, String scenarioSnapshot, String scammerSnapshot, String victimSnapshot,
            long scammerPromptId, long victimPromptId, long narratorPromptId, long reportPromptId,
            String runtimeSnapshot, Instant startedAt, Instant receivedAt, String timingEvidence, int initialAvailableFundsYuan) {
        this(userId, startRequestId, scenarioId, scammerProfileId, victimProfileId, scenarioSnapshot, scammerSnapshot,
                victimSnapshot, scammerPromptId, victimPromptId, narratorPromptId, reportPromptId, runtimeSnapshot,
                startedAt, receivedAt, timingEvidence, initialAvailableFundsYuan, false);
    }

    /**
     * @param v35NoDeadline V1.4 sessions have no business deadline; the legacy overload keeps the original 600 s so
     *                      PRD-V3.4 rows are still created exactly as before.
     */
    public SimulationSession(long userId, String startRequestId, long scenarioId, long scammerProfileId,
            long victimProfileId, String scenarioSnapshot, String scammerSnapshot, String victimSnapshot,
            long scammerPromptId, long victimPromptId, long narratorPromptId, long reportPromptId,
            String runtimeSnapshot, Instant startedAt, Instant receivedAt, String timingEvidence,
            int initialAvailableFundsYuan, boolean v35NoDeadline) {
        this.userId = userId;
        this.startRequestId = startRequestId;
        this.scenarioId = scenarioId;
        this.scammerProfileId = scammerProfileId;
        this.victimProfileId = victimProfileId;
        this.snapshotSchemaVersion = 2;
        this.initialAvailableFundsYuan = initialAvailableFundsYuan;
        this.availableBalanceYuan = initialAvailableFundsYuan;
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
        this.deadlineAt = v35NoDeadline ? null : startedAt.plusMillis(600_000);
        this.pausedTotalMs = 0;
    }

    public int getInitialAvailableFundsYuan() { return initialAvailableFundsYuan; }
    public void initializeActiveDuration(long durationMs) {
        if (currentRoundNo != 0 || durationMs <= 0) throw new IllegalArgumentException("Invalid initial active duration");
        deadlineAt = startedAt.plusMillis(durationMs);
    }
    public int getAvailableBalanceYuan() { return availableBalanceYuan; }
    public int getTotalPaidYuan() { return totalPaidYuan; }
    public int getTotalRefundedYuan() { return totalRefundedYuan; }
    public int getActualLossYuan() { return actualLossYuan; }
    public int getAdditionalPaymentCount() { return additionalPaymentCount; }
    public int getReserveReleasedYuan() { return generalReserveReleasedYuan + emergencyReserveReleasedYuan; }

    public int getSnapshotSchemaVersion() { return snapshotSchemaVersion; }
    public FundsSummary getFundsSummary() {
        return new FundsSummary(initialAvailableFundsYuan, availableBalanceYuan, totalPaidYuan, totalRefundedYuan,
                totalRewardedYuan, actualLossYuan, additionalPaymentCount, generalReserveReleasedYuan, emergencyReserveReleasedYuan);
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
    public InterventionMode getInterventionMode() { return interventionMode; }
    public String getSceneRuleSnapshot() { return sceneRuleSnapshot; }
    public void freezeSceneRules(String json) { this.sceneRuleSnapshot = json; }
    public void setInterventionMode(InterventionMode mode) { this.interventionMode = mode == null ? InterventionMode.NONE : mode; }
    public boolean isAdminTest() { return interventionMode == InterventionMode.ADMIN_TEST; }
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

    public void applyFunds(FundsSummary f) {
        applyFunds(f, false);
    }

    public void applyFunds(FundsSummary f, boolean splitRules) {
        availableBalanceYuan = f.availableBalanceYuan();
        totalPaidYuan = f.totalPaidYuan();
        totalRefundedYuan = f.totalRefundedYuan();
        totalRewardedYuan = f.totalRewardedYuan();
        actualLossYuan = f.actualLossYuan();
        additionalPaymentCount = f.additionalPaymentCount();
        int generalTotal = splitRules ? f.generalReserveReleasedYuan() : generalReserveTotalYuan + emergencyReserveTotalYuan == 0
                ? f.reserveReleasedYuan() : generalReserveTotalYuan;
        generalReserveReleasedYuan = Math.min(f.reserveReleasedYuan(), generalTotal);
        emergencyReserveReleasedYuan = f.reserveReleasedYuan() - generalReserveReleasedYuan;
    }

    public void initializeReserveTotals(int general, int emergency) {
        generalReserveTotalYuan = general;
        emergencyReserveTotalYuan = emergency;
    }

    public void freezeTrust(Long eventPromptId, int score, java.math.BigDecimal positive, java.math.BigDecimal negative,
            int smallCap, TrustPolicy policy) {
        freezeTrust(eventPromptId, score, positive, negative, smallCap, policy, false);
    }

    /** V1.4 freezes the whole configuration on snapshot schema 4 (no deadline, trust-v3). */
    public void freezeTrust(Long eventPromptId, int score, java.math.BigDecimal positive, java.math.BigDecimal negative,
            int smallCap, TrustPolicy policy, boolean v35) {
        this.eventPromptId = eventPromptId;
        snapshotSchemaVersion = v35 ? 4 : 3;
        initialTrustScore = score;
        currentTrustScore = score;
        trustPositiveFactor = positive;
        trustNegativeFactor = negative;
        trustSmallCapYuan = smallCap;
        trustThresholdAccept = policy.getTrustThresholdAccept();
        trustThresholdSmall = policy.getTrustThresholdSmall();
        trustThresholdLarge = policy.getTrustThresholdLarge();
    }

    public void applyTrustScore(int score) { currentTrustScore = score; }
    /** Only called inside the whole-round settlement transaction: true while any counterevidence subject is open. */
    public void applyCounterevidence(boolean active) { counterevidenceActive = active; }
    public boolean isCounterevidenceActive() { return counterevidenceActive; }
    public void allowEarlyFinish() { minimumRoundsException = 1; }

    public void bumpVersion() { stateVersion++; }
    public void enterRound(int roundNo) { currentRoundNo = roundNo; bumpVersion(); }
    public void pause(Instant now) { pausedAt = now; bumpVersion(); }

    /** Settles the credited pause time (capped at 300 s per run) into the deadline and clears the pause. */
    public long settlePause(Instant now) {
        if (pausedAt == null) return 0;
        if (deadlineAt == null) {
            // V1.4 has no business deadline and no pause credit; only the reading stay is restored by the caller.
            pausedAt = null;
            return 0;
        }
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

    /**
     * V1.4 verdict: exactly one winner and the frozen final trust score; no limit reason and no interruption. The
     * {@code ck_session_winner}/{@code ck_session_minimum_rounds} checks enforce winner/outcome/round agreement.
     */
    public void finishV35(OutcomeCode outcome, String reason, int finalScore, Instant now) {
        if (outcome != OutcomeCode.SCAMMER_WIN && outcome != OutcomeCode.VICTIM_WIN)
            throw new IllegalArgumentException("V3.5 verdict requires SCAMMER_WIN or VICTIM_WIN");
        if (finalScore < 0 || finalScore > 100) throw new IllegalArgumentException("Invalid final trust score");
        this.winner = outcome == OutcomeCode.SCAMMER_WIN ? "SCAMMER" : "VICTIM";
        this.finalTrustScore = finalScore;
        finish(outcome, reason, null, now);
    }

    public String getWinner() { return winner; }
    public Integer getFinalTrustScore() { return finalTrustScore; }

    public void interrupt(InterruptionCode code, Instant now) {
        status = SimulationStatus.INTERRUPTED;
        interruptionCode = code;
        interruptionDetail = code.message();
        endedAt = now;
        bumpVersion();
    }
}

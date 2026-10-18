package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "simulation_round")
public class SimulationRound {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "session_id", nullable = false)
    private Long sessionId;
    @Column(name = "round_no", nullable = false, columnDefinition = "tinyint unsigned")
    private Integer roundNo;
    @Enumerated(EnumType.STRING) @Column(name = "phase", nullable = false, length = 32)
    private RoundPhase phase;
    @Column(name = "fraud_stage", columnDefinition = "text")
    private String fraudStage;
    @Column(name = "speech_analysis", columnDefinition = "text")
    private String speechAnalysis;
    @Column(name = "method_analysis", columnDefinition = "text")
    private String methodAnalysis;
    @Column(name = "victim_psychology", columnDefinition = "text")
    private String victimPsychology;
    @Column(name = "risk_score", columnDefinition = "tinyint unsigned")
    private Integer riskScore;
    @Column(name = "risk_reason", columnDefinition = "text")
    private String riskReason;
    @Column(name = "actual_fraud_occurred", columnDefinition = "tinyint unsigned")
    private Integer actualFraudOccurred;
    @Column(name = "goal_achieved", columnDefinition = "tinyint unsigned")
    private Integer goalAchieved;
    @Column(name = "cooperation_stopped", columnDefinition = "tinyint unsigned")
    private Integer cooperationStopped;
    @Column(name = "evidence_json", columnDefinition = "json")
    private String evidenceJson;
    @Enumerated(EnumType.STRING) @Column(name = "candidate_outcome", length = 24)
    private OutcomeCode candidateOutcome;
    @Column(name = "candidate_reason", columnDefinition = "text")
    private String candidateReason;
    @Column(name = "analysis_saved_at", columnDefinition = "datetime(3)")
    private Instant analysisSavedAt;
    @Column(name = "analysis_presented_at", columnDefinition = "datetime(3)")
    private Instant analysisPresentedAt;
    @Column(name = "reading_until", columnDefinition = "datetime(3)")
    private Instant readingUntil;
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;

    protected SimulationRound() {}

    public SimulationRound(long sessionId, int roundNo, Instant now) {
        this.sessionId = sessionId;
        this.roundNo = roundNo;
        this.phase = RoundPhase.SCAMMER_GENERATING;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public long getSessionId() { return sessionId; }
    public int getRoundNo() { return roundNo; }
    public RoundPhase getPhase() { return phase; }
    public String getFraudStage() { return fraudStage; }
    public String getSpeechAnalysis() { return speechAnalysis; }
    public String getMethodAnalysis() { return methodAnalysis; }
    public String getVictimPsychology() { return victimPsychology; }
    public Integer getRiskScore() { return riskScore; }
    public String getRiskReason() { return riskReason; }
    public boolean isActualFraudOccurred() { return actualFraudOccurred != null && actualFraudOccurred == 1; }
    public boolean isGoalAchieved() { return goalAchieved != null && goalAchieved == 1; }
    public boolean isCooperationStopped() { return cooperationStopped != null && cooperationStopped == 1; }
    public String getEvidenceJson() { return evidenceJson; }
    public OutcomeCode getCandidateOutcome() { return candidateOutcome; }
    public String getCandidateReason() { return candidateReason; }
    public Instant getAnalysisSavedAt() { return analysisSavedAt; }
    public Instant getAnalysisPresentedAt() { return analysisPresentedAt; }
    public Instant getReadingUntil() { return readingUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean hasAnalysis() { return analysisSavedAt != null; }

    public void setPhase(RoundPhase phase) { this.phase = phase; }

    public void saveAnalysis(String fraudStage, String speechAnalysis, String methodAnalysis, String victimPsychology,
            int riskScore, String riskReason, boolean actualFraud, boolean goal, boolean stopped, String evidenceJson,
            OutcomeCode candidateOutcome, String candidateReason, Instant now) {
        this.fraudStage = fraudStage;
        this.speechAnalysis = speechAnalysis;
        this.methodAnalysis = methodAnalysis;
        this.victimPsychology = victimPsychology;
        this.riskScore = riskScore;
        this.riskReason = riskReason;
        this.actualFraudOccurred = actualFraud ? 1 : 0;
        this.goalAchieved = goal ? 1 : 0;
        this.cooperationStopped = stopped ? 1 : 0;
        this.evidenceJson = evidenceJson;
        this.candidateOutcome = candidateOutcome;
        this.candidateReason = candidateReason;
        this.analysisSavedAt = now;
        this.phase = RoundPhase.ANALYSIS_PRESENTING;
    }

    public void completePresentation(Instant now, Instant readingUntil) {
        this.analysisPresentedAt = now;
        this.readingUntil = readingUntil;
        this.phase = RoundPhase.COMPLETE;
    }

    public void shiftReading(long millis) { if (readingUntil != null) readingUntil = readingUntil.plusMillis(millis); }
    public void capReading(Instant at) { if (readingUntil != null && readingUntil.isAfter(at)) readingUntil = at; }
}

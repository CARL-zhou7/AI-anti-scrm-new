package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "simulation_trust_event")
public class SimulationTrustEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "bigint unsigned")
    private Long id;
    @Column(name = "session_id", columnDefinition = "bigint unsigned")
    private Long sessionId;
    @Column(name = "round_no", columnDefinition = "tinyint unsigned")
    private int roundNo;
    @Column(name = "message_id", columnDefinition = "bigint unsigned")
    private Long messageId;
    @Column(name = "event_type", columnDefinition = "varchar(32)")
    private String eventType;
    @Column(name = "subject_key", columnDefinition = "varchar(128)")
    private String subjectKey;
    @Column(name = "progress_key", columnDefinition = "varchar(128)")
    private String progressKey;
    @Column(name = "counterevidence_level", columnDefinition = "varchar(16)")
    private String counterevidenceLevel;
    @Column(name = "target_score", columnDefinition = "tinyint unsigned")
    private Integer targetScore;
    @Column(name = "constraint_active", nullable = false)
    private boolean constraintActive;
    @Column(name = "base_delta", columnDefinition = "smallint")
    private int baseDelta;
    @Column(name = "factor", precision = 3, scale = 2)
    private java.math.BigDecimal factor;
    @Column(name = "delta", columnDefinition = "smallint")
    private int delta;
    @Column(name = "score_before", columnDefinition = "tinyint unsigned")
    private int scoreBefore;
    @Column(name = "score_after", columnDefinition = "tinyint unsigned")
    private int scoreAfter;
    @Column(name = "evidence_source", columnDefinition = "varchar(16)")
    private String evidenceSource;
    @Column(name = "evidence_quote", columnDefinition = "varchar(255)")
    private String evidenceQuote;
    @Column(name = "created_at", columnDefinition = "datetime(3)", insertable = false, updatable = false)
    private Instant createdAt;

    protected SimulationTrustEvent() {}
    public SimulationTrustEvent(long sessionId, int roundNo, Long messageId,
            com.aiscam.antiscam.simulation.rules.TrustRules.ScoredEvent event, String source, String quote) {
        this(sessionId, roundNo, messageId, event, source, quote, event.subjectKey());
    }
    public SimulationTrustEvent(long sessionId, int roundNo, Long messageId,
            com.aiscam.antiscam.simulation.rules.TrustRules.ScoredEvent event, String source, String quote, String progressKey) {
        this.sessionId = sessionId;
        this.roundNo = roundNo;
        this.messageId = messageId;
        this.eventType = event.type().name();
        this.subjectKey = event.subjectKey();
        this.progressKey = progressKey;
        this.baseDelta = event.baseDelta();
        this.factor = event.factor();
        this.delta = event.delta();
        this.scoreBefore = event.scoreBefore();
        this.scoreAfter = event.scoreAfter();
        this.evidenceSource = source;
        this.evidenceQuote = quote;
    }

    /** V1.4 shape: counterevidence level, target score and constraint flag come from the scored event. */
    public SimulationTrustEvent(long sessionId, int roundNo, Long messageId,
            com.aiscam.antiscam.simulation.rules.TrustV3Rules.ScoredEvent event, String source, String quote) {
        this.sessionId = sessionId;
        this.roundNo = roundNo;
        this.messageId = messageId;
        this.eventType = event.type().name();
        this.subjectKey = event.subjectKey();
        this.progressKey = event.progressKey() == null ? event.subjectKey() : event.progressKey();
        this.counterevidenceLevel = event.counterevidenceLevel() == null ? null : event.counterevidenceLevel().name();
        this.targetScore = event.targetScore();
        this.constraintActive = event.constraintActive();
        this.baseDelta = event.baseDelta();
        this.factor = event.factor();
        this.delta = event.delta();
        this.scoreBefore = event.scoreBefore();
        this.scoreAfter = event.scoreAfter();
        this.evidenceSource = source;
        this.evidenceQuote = quote;
    }
    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public int getRoundNo() { return roundNo; }
    public Long getMessageId() { return messageId; }
    public String getEventType() { return eventType; }
    public String getSubjectKey() { return subjectKey; }
    public String getProgressKey() { return progressKey; }
    public String getCounterevidenceLevel() { return counterevidenceLevel; }
    public Integer getTargetScore() { return targetScore; }
    public boolean isConstraintActive() { return constraintActive; }
    public int getBaseDelta() { return baseDelta; }
    public java.math.BigDecimal getFactor() { return factor; }
    public int getDelta() { return delta; }
    public int getScoreBefore() { return scoreBefore; }
    public int getScoreAfter() { return scoreAfter; }
    public String getEvidenceSource() { return evidenceSource; }
    public String getEvidenceQuote() { return evidenceQuote; }
    public Instant getCreatedAt() { return createdAt; }
}

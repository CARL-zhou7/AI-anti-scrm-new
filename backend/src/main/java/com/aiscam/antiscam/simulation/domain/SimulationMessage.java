package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** A saved role line. Text fields are immutable after insert; the internal note is for Agents/report only. */
@Entity
@Table(name = "simulation_message")
public class SimulationMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "round_id", nullable = false)
    private Long roundId;
    @Enumerated(EnumType.STRING) @Column(name = "speaker_role", nullable = false, length = 16)
    private SpeakerRole speakerRole;
    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "text")
    private String content;
    @Column(name = "action_text", updatable = false, columnDefinition = "text")
    private String actionText;
    @Column(name = "internal_note", updatable = false, columnDefinition = "text")
    private String internalNote;
    @Enumerated(EnumType.STRING) @Column(name = "presentation_status", nullable = false, length = 16)
    private PresentationStatus presentationStatus;
    @Column(name = "presentation_completed_at", columnDefinition = "datetime(3)")
    private Instant presentationCompletedAt;
    @Column(name = "presentation_error_code", length = 32)
    private String presentationErrorCode;
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;

    protected SimulationMessage() {}

    public SimulationMessage(long roundId, SpeakerRole role, String content, String actionText, String internalNote,
            Instant now) {
        this.roundId = roundId;
        this.speakerRole = role;
        this.content = content;
        this.actionText = actionText;
        this.internalNote = internalNote;
        this.presentationStatus = PresentationStatus.PENDING;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public long getRoundId() { return roundId; }
    public SpeakerRole getSpeakerRole() { return speakerRole; }
    public String getContent() { return content; }
    public String getActionText() { return actionText; }
    /** Only Agent context assembly (narrator) and the report task may read this. */
    public String internalNoteForAgents() { return internalNote; }
    public PresentationStatus getPresentationStatus() { return presentationStatus; }
    public Instant getPresentationCompletedAt() { return presentationCompletedAt; }
    public String getPresentationErrorCode() { return presentationErrorCode; }
    public Instant getCreatedAt() { return createdAt; }

    public void markPresenting() { presentationStatus = PresentationStatus.PRESENTING; }
    public void complete(Instant now) { presentationStatus = PresentationStatus.COMPLETED; presentationCompletedAt = now; }
    public void fail(String errorCode) { presentationStatus = PresentationStatus.FAILED; presentationErrorCode = errorCode; }
}

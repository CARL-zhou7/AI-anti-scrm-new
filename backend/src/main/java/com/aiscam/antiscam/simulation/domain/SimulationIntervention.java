package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * The traceable record of one adopted administrator intervention. It is only created when the final content differs
 * from the model's original line, never for a plain "continue".
 */
@Entity
@Table(name = "simulation_intervention")
public class SimulationIntervention {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "bigint unsigned")
    private Long id;
    @Column(name = "session_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long sessionId;
    @Column(name = "round_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long roundId;
    @Column(name = "round_no", nullable = false, columnDefinition = "tinyint unsigned")
    private int roundNo;
    @Column(name = "draft_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long draftId;
    @Column(name = "final_message_id", columnDefinition = "bigint unsigned")
    private Long finalMessageId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16)
    private InterventionStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "mode", nullable = false, length = 16)
    private InterventionMode mode;
    @Column(name = "scene_code", nullable = false, length = 32)
    private String sceneCode;
    @Column(name = "admin_user_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long adminUserId;
    @Column(name = "original_content", nullable = false, columnDefinition = "text")
    private String originalContent;
    @Column(name = "original_action", columnDefinition = "text")
    private String originalAction;
    @Column(name = "final_content", nullable = false, columnDefinition = "text")
    private String finalContent;
    @Column(name = "final_action", columnDefinition = "text")
    private String finalAction;
    @Column(name = "intervention_note", length = 500)
    private String interventionNote;
    @Column(name = "previous_context", columnDefinition = "text")
    private String previousContext;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant updatedAt;

    protected SimulationIntervention() {}

    public SimulationIntervention(long sessionId, long roundId, int roundNo, long draftId, String sceneCode,
            long adminUserId, String originalContent, String originalAction, String finalContent, String finalAction,
            String interventionNote, InterventionMode mode) {
        this.sessionId = sessionId;
        this.roundId = roundId;
        this.roundNo = roundNo;
        this.draftId = draftId;
        this.sceneCode = sceneCode;
        this.adminUserId = adminUserId;
        this.originalContent = originalContent;
        this.originalAction = originalAction;
        this.finalContent = finalContent;
        this.finalAction = finalAction;
        this.interventionNote = interventionNote;
        this.mode = mode;
        this.status = InterventionStatus.PENDING;
    }

    public Long getId() { return id; }
    public long getSessionId() { return sessionId; }
    public long getRoundId() { return roundId; }
    public int getRoundNo() { return roundNo; }
    public long getDraftId() { return draftId; }
    public Long getFinalMessageId() { return finalMessageId; }
    public InterventionStatus getStatus() { return status; }
    public InterventionMode getMode() { return mode; }
    public String getSceneCode() { return sceneCode; }
    public Long getAdminUserId() { return adminUserId; }
    public String getOriginalContent() { return originalContent; }
    public String getOriginalAction() { return originalAction; }
    public String getFinalContent() { return finalContent; }
    public String getFinalAction() { return finalAction; }
    public String getInterventionNote() { return interventionNote; }
    public String getPreviousContext() { return previousContext; }
    public void setPreviousContext(String context) { previousContext = context; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** The formal message exists, so the record is adopted and waits for the knowledge base (option A). */
    public void link(long messageId) {
        if (finalMessageId != null && finalMessageId == messageId) return;
        if (finalMessageId != null && finalMessageId != messageId)
            throw new IllegalStateException("Intervention is already linked to another message");
        this.finalMessageId = messageId;
        this.status = InterventionStatus.ADOPTED;
    }

    /** Called when this record becomes the source of a scene rule; a source may only be linked once. */
    public void linkToRule() {
        if (status == InterventionStatus.PENDING)
            throw new IllegalStateException("An intervention without a formal message cannot enter a rule");
        this.status = InterventionStatus.LINKED;
    }
}

package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One scammer line prepared for an ADMIN_TEST round and held for review. The original line is kept so an adopted
 * intervention always records what the model proposed next to what the administrator approved.
 */
@Entity
@Table(name = "simulation_scammer_draft")
public class SimulationScammerDraft {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "bigint unsigned")
    private Long id;
    @Column(name = "session_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long sessionId;
    @Column(name = "round_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long roundId;
    @Column(name = "round_no", nullable = false, columnDefinition = "tinyint unsigned")
    private int roundNo;
    @Column(name = "revision_no", nullable = false, columnDefinition = "int unsigned")
    private int revisionNo;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16)
    private ScammerDraftStatus status;
    @Column(name = "original_content", nullable = false, columnDefinition = "text")
    private String originalContent;
    @Column(name = "original_action", columnDefinition = "text")
    private String originalAction;
    @Column(name = "draft_content", nullable = false, columnDefinition = "text")
    private String draftContent;
    @Column(name = "draft_action", columnDefinition = "text")
    private String draftAction;
    @Column(name = "guidance_text", length = 500)
    private String guidanceText;
    @Column(name = "claimed_refund_yuan")
    private Integer claimedRefundYuan;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "fund_actions", columnDefinition = "json")
    private java.util.List<FundAction> fundActions;
    @Column(name = "created_by", nullable = false, columnDefinition = "bigint unsigned")
    private Long createdBy;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant updatedAt;

    protected SimulationScammerDraft() {}

    public SimulationScammerDraft(long sessionId, long roundId, int roundNo, int revisionNo, String originalContent,
            String originalAction, String draftContent, String draftAction, String guidanceText, long createdBy) {
        this.sessionId = sessionId;
        this.roundId = roundId;
        this.roundNo = roundNo;
        this.revisionNo = revisionNo;
        this.status = ScammerDraftStatus.DRAFT;
        this.originalContent = originalContent;
        this.originalAction = originalAction;
        this.draftContent = draftContent;
        this.draftAction = draftAction;
        this.guidanceText = guidanceText;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public long getSessionId() { return sessionId; }
    public long getRoundId() { return roundId; }
    public int getRoundNo() { return roundNo; }
    public int getRevisionNo() { return revisionNo; }
    public ScammerDraftStatus getStatus() { return status; }
    public String getOriginalContent() { return originalContent; }
    public String getOriginalAction() { return originalAction; }
    public String getDraftContent() { return draftContent; }
    public String getDraftAction() { return draftAction; }
    public String getGuidanceText() { return guidanceText; }
    public Integer getClaimedRefundYuan() { return claimedRefundYuan; }
    public java.util.List<FundAction> getFundActions() { return fundActions == null ? java.util.List.of() : fundActions; }
    public void setFunds(Integer claimed, java.util.List<FundAction> actions) {
        claimedRefundYuan = claimed;
        fundActions = java.util.List.copyOf(actions);
    }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isActive() { return status == ScammerDraftStatus.DRAFT; }

    /** Editing a draft keeps the row and bumps the revision, so a late request can be rejected by revision. */
    public void revise(String content, String action, String guidance, int expectedRevisionNo) {
        if (status != ScammerDraftStatus.DRAFT) throw new IllegalStateException("Only a draft can be revised");
        if (revisionNo != expectedRevisionNo) throw new IllegalStateException("Draft revision changed");
        this.draftContent = content;
        this.draftAction = action;
        if (guidance != null) this.guidanceText = guidance;
        this.revisionNo = expectedRevisionNo + 1;
    }

    public void adopt() {
        if (status != ScammerDraftStatus.DRAFT) throw new IllegalStateException("Only a draft can be adopted");
        this.status = ScammerDraftStatus.ADOPTED;
    }

    public void discard() {
        if (status != ScammerDraftStatus.DRAFT) throw new IllegalStateException("Only a draft can be discarded");
        this.status = ScammerDraftStatus.DISCARDED;
    }
}

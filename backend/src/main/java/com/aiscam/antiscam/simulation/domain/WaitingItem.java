package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "simulation_waiting_item")
public class WaitingItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "bigint unsigned")
    private Long id;
    @Column(name = "session_id", columnDefinition = "bigint unsigned")
    private Long sessionId;
    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", length = 24)
    private WaitingItemType itemType;
    @Column(name = "subject_key", columnDefinition = "varchar(128)")
    private String subjectKey;
    @Column(name = "request_text", columnDefinition = "varchar(255)")
    private String requestText;
    @Column(name = "basis_text", columnDefinition = "varchar(255)")
    private String basisText;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private WaitingItemStatus status;
    @Column(name = "resolution_text", columnDefinition = "varchar(255)")
    private String resolutionText;
    @Column(name = "opened_round_no", columnDefinition = "tinyint unsigned")
    private int openedRoundNo;
    @Column(name = "resolved_round_no", columnDefinition = "tinyint unsigned")
    private Integer resolvedRoundNo;
    @Column(name = "created_at", columnDefinition = "datetime(3)", insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", columnDefinition = "datetime(3)", insertable = false, updatable = false)
    private Instant updatedAt;

    protected WaitingItem() {}
    public WaitingItem(long sessionId, WaitingItemType type, String subject, String text, String basis, int round) {
        this.sessionId=sessionId; this.itemType=type; this.subjectKey=subject;
        this.requestText=text; this.basisText=basis; this.openedRoundNo=round; this.status=WaitingItemStatus.OPEN;
    }
    public void refreshRequest(String text, String basis) {
        if (status == WaitingItemStatus.OPEN) { requestText=text; basisText=basis; }
    }
    public void resolve(WaitingItemStatus next, String resolution, int round) {
        if (status == next && java.util.Objects.equals(resolutionText,resolution)) return;
        com.aiscam.antiscam.simulation.rules.WaitingItemRules.transition(status,next);
        if (round < openedRoundNo || round > 20 || (next != WaitingItemStatus.ABANDONED && (resolution == null || resolution.isBlank())))
            throw new IllegalArgumentException("Invalid waiting resolution");
        // Stopping the wait must not erase an external reply that was already received.
        if (next != WaitingItemStatus.ABANDONED || resolutionText == null) resolutionText=resolution;
        status=next; resolvedRoundNo=round;
    }
    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public WaitingItemType getItemType() { return itemType; }
    public String getSubjectKey() { return subjectKey; }
    public String getRequestText() { return requestText; }
    public String getBasisText() { return basisText; }
    public WaitingItemStatus getStatus() { return status; }
    public String getResolutionText() { return resolutionText; }
    public int getOpenedRoundNo() { return openedRoundNo; }
    public Integer getResolvedRoundNo() { return resolvedRoundNo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

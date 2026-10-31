package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "simulation_fund_flow")
public class FundFlow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "session_id", nullable = false)
    private Long sessionId;
    @Column(name = "round_id", nullable = false)
    private Long roundId;
    @Column(name = "message_id")
    private Long messageId;
    @Column(name = "related_payment_id")
    private Long relatedPaymentId;
    @Column(name = "sequence_no", nullable = false, columnDefinition = "int unsigned")
    private int sequenceNo;
    @Enumerated(EnumType.STRING) @Column(name = "flow_type", nullable = false, length = 16)
    private FundFlowType flowType;
    @Enumerated(EnumType.STRING) @Column(name = "purpose", nullable = false, length = 32)
    private FundPurpose purpose;
    @Column(name = "amount_yuan", nullable = false, columnDefinition = "int unsigned")
    private int amountYuan;
    @Column(name = "balance_after_yuan", nullable = false, columnDefinition = "int unsigned")
    private int balanceAfterYuan;
    @Column(name = "reason", length = 255)
    private String reason;
    @Column(name = "reserve_tier", length = 16)
    private String reserveTier;
    @Column(name = "occurred_at", nullable = false, columnDefinition = "datetime(3)")
    private Instant occurredAt;

    protected FundFlow() {}
    public FundFlow(long sessionId, long roundId, Long messageId, FundPosting posting, Instant now) {
        this.sessionId = sessionId;
        this.roundId = roundId;
        this.messageId = messageId;
        this.relatedPaymentId = posting.relatedPaymentId();
        this.sequenceNo = posting.sequenceNo();
        this.flowType = posting.type();
        this.purpose = posting.purpose();
        this.amountYuan = posting.amountYuan();
        this.balanceAfterYuan = posting.balanceAfterYuan();
        this.reason = posting.reason();
        this.reserveTier = posting.reserveTier() != null ? posting.reserveTier().name()
                : posting.type() == FundFlowType.RESERVE_RELEASE ? "GENERAL" : null;
        this.occurredAt = now;
    }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public Long getRoundId() { return roundId; }
    public Long getMessageId() { return messageId; }
    public Long getRelatedPaymentId() { return relatedPaymentId; }
    public int getSequenceNo() { return sequenceNo; }
    public FundFlowType getFlowType() { return flowType; }
    public FundPurpose getPurpose() { return purpose; }
    public int getAmountYuan() { return amountYuan; }
    public int getBalanceAfterYuan() { return balanceAfterYuan; }
    public String getReserveTier() { return reserveTier; }
    public void setReserveTier(String tier) { reserveTier = tier; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}

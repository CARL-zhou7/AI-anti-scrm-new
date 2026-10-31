package com.aiscam.antiscam.simulation.domain;

public record FundPosting(FundFlowType type, FundPurpose purpose, int amountYuan, int sequenceNo,
        int balanceAfterYuan, String reason, Long relatedPaymentId, FundReserveTier reserveTier) {
    public FundPosting(FundFlowType type, FundPurpose purpose, int amountYuan, int sequenceNo,
            int balanceAfterYuan, String reason, Long relatedPaymentId) {
        this(type, purpose, amountYuan, sequenceNo, balanceAfterYuan, reason, relatedPaymentId, null);
    }
}

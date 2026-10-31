package com.aiscam.antiscam.simulation.domain;

public record FundsSummary(int initialAvailableYuan, int availableBalanceYuan, int totalPaidYuan,
        int totalRefundedYuan, int totalRewardedYuan, int actualLossYuan, int additionalPaymentCount,
        int generalReserveReleasedYuan, int emergencyReserveReleasedYuan) {
    public FundsSummary(int initialAvailableYuan, int availableBalanceYuan, int totalPaidYuan, int totalRefundedYuan,
            int actualLossYuan, int additionalPaymentCount, int reserveReleasedYuan) {
        this(initialAvailableYuan, availableBalanceYuan, totalPaidYuan, totalRefundedYuan, 0, actualLossYuan,
                additionalPaymentCount, reserveReleasedYuan, 0);
    }
    public int reserveReleasedYuan() { return generalReserveReleasedYuan + emergencyReserveReleasedYuan; }
}

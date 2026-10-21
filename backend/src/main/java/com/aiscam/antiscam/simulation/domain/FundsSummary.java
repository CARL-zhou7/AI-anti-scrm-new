package com.aiscam.antiscam.simulation.domain;

public record FundsSummary(int initialAvailableYuan, int availableBalanceYuan, int totalPaidYuan,
        int totalRefundedYuan, int actualLossYuan, int additionalPaymentCount, int reserveReleasedYuan) {}

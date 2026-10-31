package com.aiscam.antiscam.simulation.domain;

import com.aiscam.antiscam.content.domain.ScenarioCode;
import java.util.List;

public record FundsContext(ScenarioCode scenarioCode, int availableBalanceYuan, int reservedFundsTotalYuan,
        int reserveReleasedYuan, int totalPaidYuan, int totalRefundedYuan, List<FundFlow> existingFlows,
        int totalRewardedYuan, int generalReserveTotalYuan, int emergencyReserveTotalYuan,
        int generalReserveReleasedYuan, int emergencyReserveReleasedYuan,
        com.aiscam.antiscam.simulation.rules.ActionGate.TrustGate gate) {
    public FundsContext(ScenarioCode scenarioCode, int availableBalanceYuan, int reservedFundsTotalYuan,
            int reserveReleasedYuan, int totalPaidYuan, int totalRefundedYuan, List<FundFlow> existingFlows) {
        this(scenarioCode, availableBalanceYuan, reservedFundsTotalYuan, reserveReleasedYuan, totalPaidYuan,
                totalRefundedYuan, existingFlows, 0, reservedFundsTotalYuan, 0, reserveReleasedYuan, 0, null);
    }
    public boolean v3() { return gate != null; }
    public FundsSummary summary() {
        int additional = Math.max(0, (int) existingFlows.stream().filter(f -> f.getFlowType() == FundFlowType.PAYMENT).count() - 1);
        return new FundsSummary(0, availableBalanceYuan, totalPaidYuan, totalRefundedYuan, totalRewardedYuan,
                Math.max(0, totalPaidYuan - totalRefundedYuan - totalRewardedYuan), additional,
                generalReserveReleasedYuan, emergencyReserveReleasedYuan);
    }
    public int releasableReserveYuan() { return Math.max(0, reservedFundsTotalYuan - reserveReleasedYuan); }
    public boolean canCover(int amountYuan) { return amountYuan >= 0 && amountYuan <= (long) availableBalanceYuan + releasableReserveYuan(); }
    public int refundCapYuan() { return scenarioCode == ScenarioCode.IMPERSONATION_LOAN ? 0 : 300; }
}

package com.aiscam.antiscam.simulation.domain;

import com.aiscam.antiscam.content.domain.ScenarioCode;
import java.util.List;

public record FundsContext(ScenarioCode scenarioCode, int availableBalanceYuan, int reservedFundsTotalYuan,
        int reserveReleasedYuan, int totalPaidYuan, int totalRefundedYuan, List<FundFlow> existingFlows) {
    public FundsSummary summary() {
        int additional = Math.max(0, (int) existingFlows.stream().filter(f -> f.getFlowType() == FundFlowType.PAYMENT).count() - 1);
        return new FundsSummary(0, availableBalanceYuan, totalPaidYuan, totalRefundedYuan,
                Math.max(0, totalPaidYuan - totalRefundedYuan), additional, reserveReleasedYuan);
    }
    public int releasableReserveYuan() { return Math.max(0, reservedFundsTotalYuan - reserveReleasedYuan); }
    public boolean canCover(int amountYuan) { return amountYuan >= 0 && amountYuan <= (long) availableBalanceYuan + releasableReserveYuan(); }
    public int refundCapYuan() { return scenarioCode == ScenarioCode.IMPERSONATION_LOAN ? 0 : 300; }
}

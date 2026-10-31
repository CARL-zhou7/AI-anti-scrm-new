package com.aiscam.antiscam.simulation.rules;

import com.aiscam.antiscam.simulation.domain.*;

public final class ActionGate {
    private ActionGate() {}
    public record TrustGate(int score, int accept, int small, int large, int smallCapYuan) {}
    public enum GateDecision { ALLOW, NEED_LARGE }

    public static GateDecision decide(TrustGate gate, FundAction action, int cumulativeUnrecoveredYuan,
            int availableYuan, int generalRemaining, int emergencyRemaining) {
        boolean payment = action.type() == FundFlowType.PAYMENT;
        boolean large = action.reserveTier() == FundReserveTier.EMERGENCY || action.amountYuan() > gate.smallCapYuan()
                || (long) cumulativeUnrecoveredYuan + (payment ? action.amountYuan() : 0) > gate.smallCapYuan()
                || (payment && action.amountYuan() > (long) availableYuan + generalRemaining);
        return gate.score() >= (large ? gate.large() : gate.small()) ? GateDecision.ALLOW : GateDecision.NEED_LARGE;
    }
}

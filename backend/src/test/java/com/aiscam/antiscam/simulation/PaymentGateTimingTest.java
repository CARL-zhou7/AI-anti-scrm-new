package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;

import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.ActionGate;
import com.aiscam.antiscam.simulation.rules.ActionGate.GateDecision;
import com.aiscam.antiscam.simulation.rules.ActionGate.TrustGate;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Payment gate timing (Phase 1, Task 2). The gate reads the round-start score and the thresholds frozen at session
 * start, so a score that rises later in the round never reopens an action the victim already refused, and a snapshot
 * taken after a later policy edit cannot change a running session. The model never supplies the score.
 */
class PaymentGateTimingTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private static final int SMALL_CAP = 200;

    /** 40/70/85 as frozen by the seeded policy; a session keeps these even if the policy row changes later. */
    private static TrustGate gate(int roundStartScore) {
        return new TrustGate(roundStartScore, 40, 70, 85, SMALL_CAP);
    }

    private static FundAction payment(int amount, FundReserveTier tier) {
        return new FundAction(FundFlowType.PAYMENT, FundPurpose.ADDITIONAL_PAYMENT, amount, 1, SpeakerRole.VICTIM,
                "转账" + amount + "元", 1, null, null, tier);
    }

    private static FundAction refund(int amount) {
        return new FundAction(FundFlowType.REFUND, FundPurpose.REFUND, amount, 1, SpeakerRole.SCAMMER,
                "返还" + amount + "元", 1, null, 10L, null);
    }

    @Test
    void aSmallPaymentAtSixtyNineIsRefusedAndLiftingTheScoreLaterInTheRoundDoesNotReopenIt() {
        var beforeThisRound = payment(100, FundReserveTier.GENERAL);
        assertEquals(GateDecision.NEED_LARGE,
                ActionGate.decide(gate(69), beforeThisRound, 0, 5000, 2000, 1000));

        // The whole-round event settlement may lift the ledger to 72 later in the same round; the gate for that
        // round was frozen at 69, so the already-refused action stays refused.
        assertEquals(GateDecision.NEED_LARGE,
                ActionGate.decide(gate(69), beforeThisRound, 0, 5000, 2000, 1000),
                "a later score never retroactively allows the action");
        assertEquals(GateDecision.ALLOW,
                ActionGate.decide(gate(70), beforeThisRound, 0, 5000, 2000, 1000),
                "the next round starts from the settled score and may allow the same amount");
        assertEquals(GateDecision.ALLOW,
                ActionGate.decide(gate(72), beforeThisRound, 0, 5000, 2000, 1000));
    }

    @Test
    void largePaymentsNeedTheEightFiveThreshold() {
        var large = payment(SMALL_CAP + 1, FundReserveTier.GENERAL);
        assertEquals(GateDecision.NEED_LARGE, ActionGate.decide(gate(80), large, 0, 9000, 3000, 2000));
        assertEquals(GateDecision.ALLOW, ActionGate.decide(gate(85), large, 0, 9000, 3000, 2000));
        assertEquals(GateDecision.NEED_LARGE, ActionGate.decide(gate(84), large, 0, 9000, 3000, 2000));
    }

    @Test
    void emergencyReserveIsAlwaysLargeAndCumulativeExposureEscalatesTheGate() {
        assertEquals(GateDecision.NEED_LARGE, ActionGate.decide(gate(70),
                payment(50, FundReserveTier.EMERGENCY), 0, 5000, 2000, 1000));
        // 150 already unrecovered + 60 would cross the 200 cap, so the small gate no longer applies.
        assertEquals(GateDecision.NEED_LARGE, ActionGate.decide(gate(70),
                payment(60, FundReserveTier.GENERAL), 150, 5000, 2000, 1000));
        assertEquals(GateDecision.ALLOW, ActionGate.decide(gate(85),
                payment(60, FundReserveTier.GENERAL), 150, 5000, 2000, 1000));
        // A refund never escalates the gate and does not count towards unrecovered exposure.
        assertEquals(GateDecision.ALLOW, ActionGate.decide(gate(70), refund(180), 180, 5000, 2000, 1000));
    }

    @Test
    void aPaymentBeyondAvailableFundsAndGeneralReserveNeedsTheLargeThreshold() {
        assertEquals(GateDecision.NEED_LARGE, ActionGate.decide(gate(80),
                payment(150, FundReserveTier.GENERAL), 0, 100, 20, 1000));
        assertEquals(GateDecision.ALLOW, ActionGate.decide(gate(85),
                payment(150, FundReserveTier.GENERAL), 0, 100, 20, 1000),
                "the emergency reserve is a funds question, not a trust shortcut");
    }

    @Test
    void thresholdsAreFrozenPerSessionAndAModelScoreIsNeverAnInput() {
        var small = payment(100, FundReserveTier.GENERAL);
        var session = new SimulationSession(1, "gate", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4,
                "{\"ruleset_version\":\"PRD-V3.5\",\"trust_config\":{\"trust_ruleset_version\":\"trust-v3\"}}",
                NOW, NOW, "{}", 10000);
        var policy = org.mockito.Mockito.mock(com.aiscam.antiscam.simulation.domain.TrustPolicy.class);
        org.mockito.Mockito.when(policy.getTrustThresholdAccept()).thenReturn(40);
        org.mockito.Mockito.when(policy.getTrustThresholdSmall()).thenReturn(70);
        org.mockito.Mockito.when(policy.getTrustThresholdLarge()).thenReturn(85);
        session.freezeTrust(null, 69, java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, SMALL_CAP, policy, true);

        assertEquals(40, session.getTrustThresholdAccept());
        assertEquals(70, session.getTrustThresholdSmall());
        assertEquals(85, session.getTrustThresholdLarge());
        assertEquals(69, session.getInitialTrustScore());
        assertEquals(4, session.getSnapshotSchemaVersion(), "V3.5 freezes snapshot schema 4");

        // The gate is built from the frozen session fields only: no model output can raise the score it uses.
        var frozen = new TrustGate(session.getInitialTrustScore(), session.getTrustThresholdAccept(),
                session.getTrustThresholdSmall(), session.getTrustThresholdLarge(), SMALL_CAP);
        assertEquals(GateDecision.NEED_LARGE, ActionGate.decide(frozen, small, 0, 9000, 3000, 2000));
        assertEquals(GateDecision.NEED_LARGE, ActionGate.decide(frozen, small, 0, 9000, 3000, 2000),
                "the decision is a pure function of the frozen gate, so repeating it cannot drift");
    }
}

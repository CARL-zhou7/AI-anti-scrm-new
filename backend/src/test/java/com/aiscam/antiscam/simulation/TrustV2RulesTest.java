package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.*;
import com.aiscam.antiscam.simulation.rules.ActionGate.TrustGate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrustV2RulesTest {
    private FundPosting payment(int amount, int sequence) {
        return new FundPosting(FundFlowType.PAYMENT, FundPurpose.OTHER_PAYMENT, amount, sequence, 10000, null, null);
    }
    private FundsContext context(ScenarioCode scene, List<FundFlow> flows) {
        return new FundsContext(scene, 10000, 0, 0, 100, 0, flows, 0, 0, 0, 0, 0,
                new TrustGate(85, 40, 70, 85, 200));
    }
    @Test void allScenesNeedSmallPaymentFollowedByAdditionalUnrecoveredPrincipal() {
        for (var scene : ScenarioCode.values()) {
            var small = new FundFlow(1, 1, 1L, payment(100, 1), Instant.now());
            var before = context(scene, List.of(small));
            assertFalse(ScenarioGoalRules.goalAchieved(context(scene, List.of()), List.of(payment(100, 1))));
            assertFalse(ScenarioGoalRules.goalAchieved(context(scene, List.of()), List.of(payment(100, 1),payment(101, 2))));
            assertFalse(ScenarioGoalRules.goalAchieved(before, List.of(payment(100, 2))));
            assertTrue(ScenarioGoalRules.goalAchieved(before, List.of(payment(101, 2))));
            var added = new FundFlow(1, 2, 2L, payment(101, 2), Instant.now());
            assertTrue(ScenarioGoalRules.goalAchieved(context(scene, List.of(small, added)), List.of()));
        }
    }
    @Test void refundsReduceExposureButRewardsDoNotAndFirstLargePaymentIsNotSmallProgression() {
        var small = new FundFlow(1, 1, 1L, payment(100, 1), Instant.now());
        var refund = new FundFlow(1, 2, 2L, new FundPosting(FundFlowType.REFUND, FundPurpose.REFUND,
                100, 2, 10000, null, 1L), Instant.now());
        var reward = new FundFlow(1, 2, 2L, new FundPosting(FundFlowType.REWARD, FundPurpose.REWARD,
                100, 2, 10000, null, null), Instant.now());
        assertFalse(ScenarioGoalRules.goalAchieved(context(ScenarioCode.TASK_REBATE, List.of(small, refund)), List.of(payment(200, 3))));
        assertTrue(ScenarioGoalRules.goalAchieved(context(ScenarioCode.TASK_REBATE, List.of(small, reward)), List.of(payment(101, 3))));
        assertFalse(ScenarioGoalRules.goalAchieved(context(ScenarioCode.TASK_REBATE, List.of()), List.of(payment(201, 1))));
    }
    @Test void explicitStopAndLimitsDoNotFabricateIdentificationOrLargeGoal() {
        assertEquals(OutcomeCode.NOT_DEFRAUDED, OutcomeRules.expectedV2(false, false, true, false));
        assertEquals(OutcomeCode.IDENTIFIED, OutcomeRules.expectedV2(false, false, true, true));
        assertEquals(OutcomeCode.DEFRAUDED, OutcomeRules.expectedV2(false, true, true, false));
        assertNull(OutcomeRules.expectedV2(false, true, false, false));
        assertNull(OutcomeRules.decideV2(18, null, null, false, true));
        var limit = OutcomeRules.decideV2(20, null, null, false, true);
        assertEquals(OutcomeCode.DEFRAUDED, limit.outcome());
        assertEquals(LimitReason.ROUND_LIMIT, limit.limitReason());
        assertEquals("已受骗（小额投入）", OutcomeRules.displayV2(limit.outcome(), false, false));
        assertEquals("已受骗（小额投入，已止损）", OutcomeRules.displayV2(OutcomeCode.DEFRAUDED, false, true));
        assertEquals("大额投入目标达成", OutcomeRules.displayV2(OutcomeCode.DEFRAUDED, true, true));
        assertEquals(OutcomeCode.DEFRAUDED, OutcomeRules.decideV2(12, null, null, true, true).outcome());
        assertEquals(OutcomeCode.LIMIT_REACHED, OutcomeRules.decideV2(20, null, null, false, false).outcome());
    }
    @Test void scoringAndGatesMatchTheCalibratedBaseline() {
        assertEquals(4, TrustRules.nominal(TrustEventType.NEW_EFFECTIVE_EXPLANATION.baseDelta(), new BigDecimal("1.2")));
        assertEquals(5, TrustRules.nominal(TrustEventType.DOUBT_RESOLVED.baseDelta(), new BigDecimal("1.2")));
        for (int score : new int[]{69, 70, 84, 85}) {
            var gate = new TrustGate(score, 40, 70, 85, 200);
            var action = new FundAction(FundFlowType.PAYMENT, FundPurpose.OTHER_PAYMENT, 100, 1,
                    SpeakerRole.VICTIM, "已支付100元", 1, null, null);
            assertEquals(score >= 70, ActionGate.decide(gate, action, 0, 10000, 0, 0) == ActionGate.GateDecision.ALLOW);
            assertEquals(score >= 85, ActionGate.decide(gate, action, 200, 10000, 0, 0) == ActionGate.GateDecision.ALLOW);
        }
    }
}

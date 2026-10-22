package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.FundRules;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class FundRulesTest {
    private FundsSummary funds = new FundsSummary(1000, 1000, 0, 0, 0, 0, 0);
    private final List<FundFlow> flows = new ArrayList<>();
    private FundAction action(FundFlowType type, int amount, Long payment, int order) {
        return new FundAction(type, type == FundFlowType.PAYMENT ? FundPurpose.OTHER_PAYMENT : FundPurpose.valueOf(type.name()),
                amount, 5, type == FundFlowType.REFUND ? SpeakerRole.SCAMMER : SpeakerRole.VICTIM,
                "已经转账" + amount + "元", order, type == FundFlowType.RESERVE_RELEASE ? "动用预留" : null, payment);
    }
    private FundApplication apply(ScenarioCode scenario, FundAction... actions) {
        return FundRules.apply(funds, scenario, 100, 5, flows.size() + 1, List.of(actions), flows);
    }
    private void post(FundAction action) {
        var result = apply(ScenarioCode.TASK_REBATE, action);
        for (var posting : result.postings()) {
            var row = new FundFlow(1, 1, 1L, posting, Instant.EPOCH);
            ReflectionTestUtils.setField(row, "id", (long) flows.size() + 1);
            flows.add(row);
        }
        funds = result.after();
    }
    @Test void moneyIsConservedAndFirstPaymentIsNotAdditional() {
        post(action(FundFlowType.PAYMENT, 100, null, 1));
        assertEquals(new FundsSummary(1000, 900, 100, 0, 100, 0, 0), funds);
        post(action(FundFlowType.PAYMENT, 200, null, 1));
        post(action(FundFlowType.REFUND, 50, 1L, 1));
        post(action(FundFlowType.RESERVE_RELEASE, 100, null, 1));
        assertEquals(new FundsSummary(1000, 850, 300, 50, 250, 1, 100), funds);
        assertEquals(List.of(900, 700, 750, 850), flows.stream().map(FundFlow::getBalanceAfterYuan).toList());
        assertEquals(List.of(1, 2, 3, 4), flows.stream().map(FundFlow::getSequenceNo).toList());
        assertEquals(funds, apply(ScenarioCode.TASK_REBATE).after());
        var nextRound = new SimulationRound(1, 6, Instant.EPOCH, funds);
        assertEquals(250, nextRound.getRoundActualLossYuan());
        assertEquals(1, nextRound.getAdditionalPaymentCountTotal());
        assertEquals(0, nextRound.getRoundPaidYuan());
        for (int amount : new int[]{2000, 0, -1}) assertThrows(IllegalArgumentException.class,
                () -> apply(ScenarioCode.TASK_REBATE, action(FundFlowType.PAYMENT, amount, null, 1)));
    }
    @Test void capsRefundsByScenarioPaymentAndTotalAndRejectsDuplicates() {
        post(action(FundFlowType.PAYMENT, 1000, null, 1));
        assertEquals(0, funds.availableBalanceYuan());
        post(action(FundFlowType.REFUND, 250, 1L, 1));
        for (var scenario : List.of(ScenarioCode.TASK_REBATE, ScenarioCode.FAKE_INVESTMENT)) {
            assertThrows(IllegalArgumentException.class, () -> apply(scenario, action(FundFlowType.REFUND, 51, 1L, 1)));
            assertDoesNotThrow(() -> apply(scenario, action(FundFlowType.REFUND, 50, 1L, 1)));
        }
        assertThrows(IllegalArgumentException.class, () -> apply(ScenarioCode.IMPERSONATION_LOAN, action(FundFlowType.REFUND, 1, 1L, 1)));
        assertThrows(IllegalArgumentException.class, () -> apply(ScenarioCode.TASK_REBATE, action(FundFlowType.REFUND, 1, 999L, 1)));
        var duplicate = action(FundFlowType.PAYMENT, 10, null, 1);
        assertThrows(IllegalArgumentException.class, () -> apply(ScenarioCode.TASK_REBATE, duplicate, duplicate));
        assertThrows(IllegalArgumentException.class, () -> apply(ScenarioCode.TASK_REBATE, duplicate, action(FundFlowType.PAYMENT, 10, null, 2)));
        assertEquals(0, FundRules.actualLoss(10, 20));
    }
    @Test void reserveAndPerPaymentRefundLimitsCannotBeBypassedWithinOneMessage() {
        post(action(FundFlowType.PAYMENT, 100, null, 1));
        assertThrows(IllegalArgumentException.class, () -> apply(ScenarioCode.TASK_REBATE,
                action(FundFlowType.REFUND, 60, 1L, 1), action(FundFlowType.REFUND, 50, 1L, 2)));
        post(action(FundFlowType.RESERVE_RELEASE, 80, null, 1));
        assertThrows(IllegalArgumentException.class, () -> apply(ScenarioCode.TASK_REBATE, action(FundFlowType.RESERVE_RELEASE, 30, null, 1)));
        var a = action(FundFlowType.RESERVE_RELEASE, 10, null, 1);
        assertThrows(IllegalArgumentException.class, () -> apply(ScenarioCode.TASK_REBATE,
                new FundAction(a.type(), a.purpose(), a.amountYuan(), 5, a.speaker(), a.quote(), 1, " ", null)));
        var context = new FundsContext(ScenarioCode.TASK_REBATE, 0, 100, 80, 100, 0, flows);
        assertTrue(context.canCover(20)); assertFalse(context.canCover(21));
    }
}

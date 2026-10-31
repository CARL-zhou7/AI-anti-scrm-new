package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.DialogueLine;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.FundRules;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class RefundPostingTest {
    private final FundFlow payment = flow(1, FundFlowType.PAYMENT, 50, 2950, null);
    private final FundFlow refund = flow(2, FundFlowType.REFUND, 20, 2970, 1L);
    private final FundsContext paid = new FundsContext(ScenarioCode.TASK_REBATE, 2950, 9000, 0, 50, 0, List.of(payment));
    private final FundsContext refunded = new FundsContext(ScenarioCode.TASK_REBATE, 2970, 9000, 0, 50, 20, List.of(payment, refund));

    private static FundFlow flow(long id, FundFlowType type, int amount, int balance, Long related) {
        var row = new FundFlow(49, id, id, new FundPosting(type,
                type == FundFlowType.PAYMENT ? FundPurpose.INITIAL_PAYMENT : FundPurpose.REFUND,
                amount, (int) id, balance, null, related), Instant.EPOCH);
        ReflectionTestUtils.setField(row, "id", id);
        return row;
    }

    private static FundAction action(String quote, int round) {
        return new FundAction(FundFlowType.REFUND, FundPurpose.REFUND, 20, round, SpeakerRole.SCAMMER, quote, 1, null, 1L);
    }

    @Test void promisesAndSelectiveQuotesCannotPostRefunds() {
        for (String promise : List.of("我先私下返你20块诚意金", "告知先私下返20元诚意金到对方账户",
                "愿意先返20元", "已答应返款20元", "未实际返款20元", "不会再次实际返款20元")) {
            String quote = promise.contains("实际返款20元") ? "实际返款20元" : promise;
            assertThrows(AgentCallException.class, () -> AgentOutputValidator.funds(SpeakerRole.SCAMMER,
                    "你收到再说", promise, 20, List.of(action(quote, 10)), 10, paid, List.of()), promise);
        }
    }

    @Test void arrivalConfirmationOfAnExistingRefundCannotCreateAnotherPosting() {
        var history = List.of(new DialogueLine(10, SpeakerRole.SCAMMER, "已经返款20元", null, null));
        for (String confirmation : List.of("20块诚意金已经到你账户了", "20元已经到账", "通过私聊向林晓的账户实际返款20元，并提醒其查收")) {
            assertThrows(AgentCallException.class, () -> AgentOutputValidator.funds(SpeakerRole.SCAMMER,
                    confirmation, null, 20, List.of(action(confirmation, 11)), 11, refunded, history), confirmation);
        }
    }

    @Test void promiseThenExecutedRefundThenReceiptOnlyCreditsTwentyOnce() {
        String promise = "我先私下返你20块诚意金";
        AgentOutputValidator.funds(SpeakerRole.SCAMMER, promise, null, 20, List.of(), 10, paid, List.of());
        var pending = FundRules.apply(paid.summary(), ScenarioCode.TASK_REBATE, 9000, 10, 2, List.of(), paid.existingFlows());
        assertEquals(0, pending.after().totalRefundedYuan());
        String executed = "通过私聊向林晓的账户实际返款20元，并提醒其查收";
        var actual = action(executed, 11);
        AgentOutputValidator.funds(SpeakerRole.SCAMMER, "20块诚意金已经到你账户了", executed, 20, List.of(actual),
                11, paid, List.of(new DialogueLine(10, SpeakerRole.SCAMMER, promise, null, null)));
        var posted = FundRules.apply(pending.after(), ScenarioCode.TASK_REBATE, 9000, 11, 2, List.of(actual), paid.existingFlows());
        AgentOutputValidator.funds(SpeakerRole.VICTIM, "我查了下，20块确实到账了", null, null, List.of(), 11, refunded, List.of());
        var receipt = FundRules.apply(posted.after(), ScenarioCode.TASK_REBATE, 9000, 11, 3, List.of(), refunded.existingFlows());
        assertEquals(20, receipt.after().totalRefundedYuan());
        assertEquals(30, receipt.after().actualLossYuan());
        assertEquals(2970, receipt.after().availableBalanceYuan());
    }

    @Test void anExplicitAdditionalCompletedRefundMayHaveTheSameAmount() {
        String extra = "又实际返款20元";
        var actual = action(extra, 12);
        assertDoesNotThrow(() -> AgentOutputValidator.funds(SpeakerRole.SCAMMER, extra, null, 20,
                List.of(actual), 12, refunded, List.of()));
        var posted = FundRules.apply(refunded.summary(), ScenarioCode.TASK_REBATE, 9000, 12, 3, List.of(actual), refunded.existingFlows());
        assertEquals(40, posted.after().totalRefundedYuan());
    }

    @Test void equalRefundAmountsForDifferentPaymentsAreNotDuplicates() {
        var otherPayment = flow(3, FundFlowType.PAYMENT, 50, 2920, null);
        var context = new FundsContext(ScenarioCode.TASK_REBATE, 2920, 9000, 0, 100, 20, List.of(payment, refund, otherPayment));
        String quote = "已经返款20元";
        var actual = new FundAction(FundFlowType.REFUND, FundPurpose.REFUND, 20, 12, SpeakerRole.SCAMMER, quote, 1, null, 3L);
        assertDoesNotThrow(() -> AgentOutputValidator.funds(SpeakerRole.SCAMMER, quote, null, 20,
                List.of(actual), 12, context, List.of()));
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.funds(SpeakerRole.SCAMMER, quote, null, 20,
                List.of(action("20元", 12)), 12, paid, List.of()));
    }

    @Test void advertisedRewardDoesNotPostButAnExecutedRewardDoes() {
        var context = new FundsContext(ScenarioCode.TASK_REBATE, 3000, 9000, 0, 0, 0, List.of(),
                0, 7500, 1500, 0, 0, new com.aiscam.antiscam.simulation.rules.ActionGate.TrustGate(50, 60, 80, 90, 100));
        String promise = "首单报酬8元，不用垫钱，您先试一单看看";
        var promised = new FundAction(FundFlowType.REWARD, FundPurpose.REWARD, 8, 2,
                SpeakerRole.SCAMMER, promise, 1, null, null);
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.funds(SpeakerRole.SCAMMER,
                promise, "发送任务说明截图", 8, List.of(promised), 2, context, List.of()));
        assertDoesNotThrow(() -> AgentOutputValidator.funds(SpeakerRole.SCAMMER,
                promise, "发送任务说明截图", 8, List.of(), 2, context, List.of()));
        String executed = "已实际发放8元报酬到账";
        var actual = new FundAction(FundFlowType.REWARD, FundPurpose.REWARD, 8, 2,
                SpeakerRole.SCAMMER, executed, 1, null, null);
        AgentOutputValidator.funds(SpeakerRole.SCAMMER, executed, null, 8, List.of(actual), 2, context, List.of());
        var posted = FundRules.apply(context.summary(), context, 2, 1, List.of(actual));
        assertEquals(8, posted.after().totalRewardedYuan());
        assertEquals(3008, posted.after().availableBalanceYuan());
    }

    @Test void scammerContextIdentifiesAlreadyPostedRefunds() {
        String context = AgentContextAssembler.scammerFunds(refunded);
        assertTrue(context.contains("返款流水ID=2"));
        assertTrue(context.contains("关联付款ID=1"));
        assertTrue(context.contains("已入账，不得重复"));
    }
    @Test void completedTransferWordingIsNotMistakenForFutureTense() {
        var context = new FundsContext(ScenarioCode.TASK_REBATE, 3000, 9000, 0, 0, 0, List.of(),
                0, 7500, 1500, 0, 0, new com.aiscam.antiscam.simulation.rules.ActionGate.TrustGate(90, 60, 80, 90, 100));
        for (String quote : List.of("已将5元转账给对方", "已经给你转了5元", "已实际发放5元报酬", "5元已经到账")) {
            var reward = new FundAction(FundFlowType.REWARD, FundPurpose.REWARD, 5, 5, SpeakerRole.SCAMMER, quote, 1, null, null);
            assertDoesNotThrow(() -> AgentOutputValidator.funds(SpeakerRole.SCAMMER, quote, null, 5,
                    List.of(reward), 5, context, List.of()), quote);
            assertEquals(5, FundRules.apply(context.summary(), context, 5, 1, List.of(reward)).after().totalRewardedYuan());
        }
        String payment = "已将50元转账至对方账户";
        var pay = new FundAction(FundFlowType.PAYMENT, FundPurpose.INITIAL_PAYMENT, 50, 5, SpeakerRole.VICTIM, payment, 1, null, null);
        assertDoesNotThrow(() -> AgentOutputValidator.funds(SpeakerRole.VICTIM, payment, null, null,
                List.of(pay), 5, context, List.of()));
        String refund = "已将20元返还给对方";
        assertDoesNotThrow(() -> AgentOutputValidator.funds(SpeakerRole.SCAMMER, refund, null, 20,
                List.of(action(refund,5)), 5, paid, List.of()));
    }

    @Test void selectiveRewardAndPaymentQuotesCannotHideNegationsOrPromises() {
        var context = new FundsContext(ScenarioCode.TASK_REBATE, 3000, 9000, 0, 0, 0, List.of(),
                0, 7500, 1500, 0, 0, new com.aiscam.antiscam.simulation.rules.ActionGate.TrustGate(90, 60, 80, 90, 100));
        for (String source : List.of("没有实际转账5元", "已答应转账5元", "做完任务后实际转账5元", "将5元转账给对方", "将实际转账5元")) {
            String quote = source.contains("实际转账") ? "实际转账5元" : source;
            for (SpeakerRole role : List.of(SpeakerRole.SCAMMER, SpeakerRole.VICTIM)) {
                var entry = new FundAction(role == SpeakerRole.SCAMMER ? FundFlowType.REWARD : FundFlowType.PAYMENT,
                        role == SpeakerRole.SCAMMER ? FundPurpose.REWARD : FundPurpose.INITIAL_PAYMENT,
                        5, 5, role, quote, 1, null, null);
                assertThrows(AgentCallException.class, () -> AgentOutputValidator.funds(role, source, null,
                        role == SpeakerRole.SCAMMER ? 5 : null, List.of(entry), 5, context, List.of()), source);
            }
        }
    }
}

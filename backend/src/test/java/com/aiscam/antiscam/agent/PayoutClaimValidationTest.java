package com.aiscam.antiscam.agent;

import static org.junit.jupiter.api.Assertions.*;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.ActionGate.TrustGate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Payout statements must describe the right transaction, rather than merely contain a familiar amount. */
class PayoutClaimValidationTest {
    @Test void combinedPrincipalAndCommissionGetsActionableFeedbackAndSeparatePayoutsPass() {
        var context = funds(List.of(flow(1, FundFlowType.PAYMENT, 100, null)));
        String combined = "已实际转账130元";
        var badRefund = new FundAction(FundFlowType.REFUND, FundPurpose.REFUND, 100, 6,
                SpeakerRole.SCAMMER, combined, 1, null, 1L);
        var error = assertThrows(AgentCallException.class, () -> PayoutClaims.validateAction(
                badRefund, combined, null, context, List.of(), 6));
        assertTrue(error.getMessage().contains("type=REFUND, amountYuan=100"));
        assertTrue(error.getMessage().contains("本息合计金额"));
        String ambiguous = "已实际退还100元报酬";
        var wrongKind = new FundAction(FundFlowType.REWARD, FundPurpose.REWARD, 100, 6,
                SpeakerRole.SCAMMER, ambiguous, 1, null, null);
        var mismatch = assertThrows(AgentCallException.class, () -> PayoutClaims.validateAction(
                wrongKind, ambiguous, null, context, List.of(), 6));
        assertTrue(mismatch.getMessage().contains("type=REWARD, amountYuan=100"));
        assertTrue(mismatch.getMessage().contains("已实际发放100元报酬"));
        String refund = "已实际退还100元本金";
        String reward = "已实际发放30元报酬";
        var actions = List.of(
                new FundAction(FundFlowType.REFUND, FundPurpose.REFUND, 100, 6,
                        SpeakerRole.SCAMMER, refund, 1, null, 1L),
                new FundAction(FundFlowType.REWARD, FundPurpose.REWARD, 30, 6,
                        SpeakerRole.SCAMMER, reward, 2, null, null));
        assertDoesNotThrow(() -> AgentOutputValidator.funds(SpeakerRole.SCAMMER,
                "本金和佣金已分别发放，请查收。", refund + "。" + reward + "。", 130,
                actions, 6, context, List.of()));
    }
    @Test void confirmedOutgoingPaymentIsNotAskedAboutAgainOrConfusedWithIncomingProfits() {
        var context = funds(List.of(flow(1, FundFlowType.PAYMENT, 286, null)));
        String contact = "您刚才的286元解冻金已确认，现在可操作提现。";
        String repeated = "提现指引V2我收到了，286元解冻金刚才已转，您看下是否到账？";
        assertEquals("提现指引V2我收到了，这笔款您已确认收到。",
                PayoutClaims.correctConfirmedPaymentQuestion(repeated, contact, context));
        assertEquals(repeated, PayoutClaims.correctConfirmedPaymentQuestion(repeated, contact, funds(List.of())));
        assertEquals(repeated, PayoutClaims.correctConfirmedPaymentQuestion(repeated, "我还没收到286元", context));
        for (String text : List.of("收益286元还没到账，我想查一下。", "另一笔300元刚才已转，您看下是否到账？"))
            assertEquals(text, PayoutClaims.correctConfirmedPaymentQuestion(text, contact, context));
    }
    private static FundFlow flow(long id, FundFlowType type, int amount, Long related) {
        FundPurpose purpose = switch (type) {
            case PAYMENT -> FundPurpose.INITIAL_PAYMENT;
            case REWARD -> FundPurpose.REWARD;
            case REFUND -> FundPurpose.REFUND;
            default -> throw new IllegalArgumentException();
        };
        var f = new FundFlow(142, id, id, new FundPosting(type, purpose, amount, (int) id,
                3000, null, related), Instant.EPOCH);
        ReflectionTestUtils.setField(f, "id", id);
        return f;
    }
    private static FundsContext funds(List<FundFlow> flows) {
        int paid = flows.stream().filter(f -> f.getFlowType() == FundFlowType.PAYMENT).mapToInt(FundFlow::getAmountYuan).sum();
        int refunded = flows.stream().filter(f -> f.getFlowType() == FundFlowType.REFUND).mapToInt(FundFlow::getAmountYuan).sum();
        int rewarded = flows.stream().filter(f -> f.getFlowType() == FundFlowType.REWARD).mapToInt(FundFlow::getAmountYuan).sum();
        return new FundsContext(ScenarioCode.TASK_REBATE, 3000, 9000, 0, paid, refunded, flows,
                rewarded, 7500, 1500, 0, 0, new TrustGate(90, 40, 70, 85, 200));
    }
    private static void validate(String line, String action, FundsContext context) {
        AgentOutputValidator.funds(SpeakerRole.SCAMMER, line, action, 8, List.of(), 5, context, List.of());
    }

    @Test void receiptDenialIsCorrectedOnlyForAProvenPayoutTheContactActuallyReferences() {
        String denied = "我没看到24元到账，下一步怎么操作？";
        String contact = "24元已真实到账";
        var credited = funds(List.of(flow(1, FundFlowType.REWARD, 24, null)));
        assertEquals("这笔24元已收到。下一步怎么操作？",
                PayoutClaims.correctReceiptDenial(denied, contact, null, credited));
        assertEquals(denied, PayoutClaims.correctReceiptDenial(denied, contact, null, funds(List.of())));
        assertEquals(denied, PayoutClaims.correctReceiptDenial(denied, "稍后再给你发24元", null, credited));
        assertEquals(denied, PayoutClaims.correctReceiptDenial(denied, "已退回24元本金", null, credited));
        for (String text : List.of("我没看到48元到账", "我不是没收到24元", "如果没收到24元怎么办", "另一笔24元还没收到", "我没收到24元本金退款", "我没看到24元转账截图"))
            assertEquals(text, PayoutClaims.correctReceiptDenial(text, contact, null, credited));
    }

    @Test void receiptCorrectionAlsoRemovesTheImmediatelyFollowingWaitForTheSameMoney() {
        var credited = funds(List.of(flow(1, FundFlowType.REFUND, 100, 2L)));
        String contact = "我们已为您实际返还100元解冻金，现在到账了，请查收。";
        String original = "解冻金100元我收到了，但没看到这笔100元到账。等它真落袋了，我再看要不要追加操作。";
        assertEquals("解冻金100元我收到了，这笔100元已收到。我再看要不要追加操作。",
                PayoutClaims.correctReceiptDenial(original, contact, null, credited));
        assertEquals("这笔100元已收到。我再考虑是否继续。",
                PayoutClaims.correctReceiptDenial("这笔100元已收到。等它真落袋了，我再考虑是否继续。", contact, null, credited));
        for (String text : List.of("这笔100元已收到。等另一笔款到账了，我再考虑。",
                "这笔100元已收到。等本金真落袋了，我再考虑。",
                "这笔100元已收到。等它下次返款了，我再考虑。",
                "这笔100元已收到。另一笔200元还没收到。等它真落袋了，我再考虑。",
                "等它真落袋了，我再考虑。"))
            assertEquals(text, PayoutClaims.correctReceiptDenial(text, contact, null, credited));
        String uncredited = "这笔100元已收到。等它真落袋了，我再考虑是否继续。";
        assertEquals(uncredited, PayoutClaims.correctReceiptDenial(uncredited, contact, null, funds(List.of())));
    }

    @Test void acknowledgementOfVictimPaymentDoesNotTurnEveryAmountIntoScammerPayout() {
        var context = funds(List.of(flow(1, FundFlowType.PAYMENT, 100, null)));
        for (String text : List.of("我已收到你支付的100元，稍后返你8元报酬", "这笔100元付款已经完成，下一笔任务金额是200元"))
            assertDoesNotThrow(() -> validate(text, null, context), text);
    }

    @Test void investmentPrincipalArrivalIsNotAnOutgoingRefund() {
        for (String text : List.of("您投入的1000元已到账", "你充值1000元已到账", "您入金的1000元已到账"))
            assertDoesNotThrow(() -> validate(text, null, funds(List.of())), text);
        for (String text : List.of("给您退回的1000元已到账", "1000元收益已到账"))
            assertThrows(AgentCallException.class, () -> validate(text, null, funds(List.of())), text);
    }

    @Test void savedEightYuanRewardCanBeReferencedAlongsideTheNewTaskAmount() {
        var context = funds(List.of(flow(1, FundFlowType.REWARD, 8, null)));
        for (String text : List.of("刚才的8元报酬已经到账，现在的任务需要垫付100元",
                "8元报酬已经到账并确认了这次100元任务，完成后再结算",
                "已收到你的点赞截图，8元报酬已发放，稍后到账注意查收"))
            assertDoesNotThrow(() -> validate(text, null, context), text);
    }

    @Test void aNewCompletedPayoutCannotOmitItsFundAction() {
        var context = funds(List.of());
        for (String text : List.of("已实际发放8元报酬", "我已经给你转账8元", "8元报酬已经到账", "你已经收到8元报酬"))
            assertThrows(AgentCallException.class, () -> validate(text, null, context), text);
    }

    @Test void anotherPayoutRequiresANewActionEvenWhenAnEqualHistoricalAmountExists() {
        var context = funds(List.of(flow(1, FundFlowType.REWARD, 8, null)));
        for (String text : List.of("又实际发放8元报酬", "再次发放了8元报酬", "另外已经给你转账8元"))
            assertThrows(AgentCallException.class, () -> validate(text, null, context), text);
    }

    @Test void completedPayoutInActionIsCheckedWhenTheSpokenLineDoesNotMentionIt() {
        assertThrows(AgentCallException.class, () -> validate("请注意查收", "已实际发放8元报酬", funds(List.of())));
    }

    @Test void aFutureConditionalPayoutAcrossCommaDoesNotClaimAnActualArrival() {
        var context = funds(List.of());
        for (String text : List.of("您完成100元付款后，返利8元会原路退回微信",
                "你支付100元后，8元报酬才会到账", "你完成100元付款后，系统会显示8元报酬已到账"))
            assertDoesNotThrow(() -> validate(text, null, context), text);
    }

    @Test void taskCompletionConditionsAndDisplayedCommissionCannotPostExecutedRewards() {
        String condition = "完成一单返8元";
        var conditional = new FundAction(FundFlowType.REWARD, FundPurpose.REWARD, 8, 2,
                SpeakerRole.SCAMMER, condition, 1, null, null);
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.funds(SpeakerRole.SCAMMER,
                "不用先付款，我发个新手任务给你试，完成一单返8元，5分钟就能搞定。",
                "发送任务截图，显示佣金8元", 8, List.of(conditional), 2, funds(List.of()), List.of()));
        String shown = "已发放8元报酬";
        var displayed = new FundAction(FundFlowType.REWARD, FundPurpose.REWARD, 8, 2,
                SpeakerRole.SCAMMER, shown, 1, null, null);
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.funds(SpeakerRole.SCAMMER,
                "这是示意页面", "发送一张截图显示已发放8元报酬", 8,
                List.of(displayed), 2, funds(List.of()), List.of()));
    }

    @Test void delayedArrivalDoesNotEraseAnExplicitCompletedDisbursement() {
        for (String text : List.of("8元报酬已发放但未到账", "已发放8元报酬，稍后到账注意查收"))
            assertThrows(AgentCallException.class, () -> validate(text, null, funds(List.of())), text);
    }

    @Test void sendingAScreenshotDoesNotExemptAnIndependentActualPayout() {
        for (String text : List.of("已实际转账8元并发送截图", "已实际发放8元报酬，截图也发给你了"))
            assertThrows(AgentCallException.class, () -> validate(text, null, funds(List.of())), text);
        assertDoesNotThrow(() -> validate("截图显示8元报酬已到账", "发送演示截图", funds(List.of())));
    }

    @Test void aRewardCannotBackAClaimThatThePrincipalWasRefunded() {
        var context = funds(List.of(flow(1, FundFlowType.PAYMENT, 100, null), flow(2, FundFlowType.REWARD, 8, null)));
        assertThrows(AgentCallException.class, () -> validate("之前的8元本金已经退回", null, context));
        assertThrows(AgentCallException.class, () -> validate("本金8元已经到账", null, context));
        var refunded = funds(List.of(flow(1, FundFlowType.PAYMENT, 100, null), flow(2, FundFlowType.REFUND, 8, 1L)));
        assertDoesNotThrow(() -> validate("之前的8元本金已经退回", null, refunded));
        assertDoesNotThrow(() -> validate("100元本金已返8元", null, refunded));
    }

    @Test void validationNeverChangesTheLedgerOrSummaryOnSuccessOrRejection() {
        var ledger = List.of(flow(1, FundFlowType.REWARD, 8, null));
        var context = funds(ledger);
        var before = context.summary();
        validate("刚才的8元报酬已经到账", null, context);
        assertThrows(AgentCallException.class, () -> validate("又实际发放8元报酬", null, context));
        assertEquals(before, context.summary());
        assertSame(ledger, context.existingFlows());
        assertEquals(1, ledger.size());
        assertEquals(8, ledger.getFirst().getAmountYuan());
    }
}

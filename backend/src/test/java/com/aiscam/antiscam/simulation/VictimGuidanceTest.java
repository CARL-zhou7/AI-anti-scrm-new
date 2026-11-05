package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;
import com.aiscam.antiscam.agent.AgentModels.DialogueLine;
import com.aiscam.antiscam.agent.AgentOutputValidator;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.*;
import com.aiscam.antiscam.simulation.snapshot.SceneRuleSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class VictimGuidanceTest {
    @Test void investmentRequestsUseTheSharedSimulationAccountAndExecuteOnTheThirdOccurrence() {
        var saved = new java.util.ArrayList<VictimGuidance>();
        var history = new java.util.ArrayList<DialogueLine>();
        VictimGuidance decision = null;
        for (int round = 10; round <= 12; round++) {
            var current = new DialogueLine(round, SpeakerRole.SCAMMER, "请投入500元进行实盘体验", null, null);
            var match = new VictimGuidance.Match("4", round == 10 ? 0 : round - 1, current.content(), 500,
                    FundPurpose.INVESTMENT_PAYMENT, "", round == 10 ? "NONE" : "REFUSED",
                    round == 10 ? "" : "我先不投入", false, "");
            decision = VictimGuidance.decide(round, match, rules, saved, history, current, false);
            saved.add(decision); history.add(current);
            history.add(new DialogueLine(round, SpeakerRole.VICTIM, "我先不投入", null, null));
        }
        assertEquals("投资平台账户", decision.recipient()); assertEquals(3, decision.consecutive());
        var context = new FundsContext(ScenarioCode.FAKE_INVESTMENT, 1000, 0, 0, 0, 0, List.of(),
                0, 0, 0, 0, 0, new ActionGate.TrustGate(37, 40, 70, 85, 1000), decision);
        assertTrue(decision.paymentAllowed(context));
        String completedPrompt = decision.complete().prompt(context);
        assertTrue(completedPrompt.contains("本事项此前已经完成"));
        assertFalse(completedPrompt.contains("本事项尚不具备执行条件"));
        assertFalse(completedPrompt.contains("本事项已经决定相信并付款"));
        var payment = new FundAction(FundFlowType.PAYMENT, FundPurpose.INVESTMENT_PAYMENT, 500, 12,
                SpeakerRole.VICTIM, "已实际转账500元至投资平台账户", 1, null, null, null);
        var result = FundRules.apply(context.summary(), context, 12, 1, List.of(payment));
        assertEquals(500, result.after().totalPaidYuan()); assertEquals(500, result.after().availableBalanceYuan());
        assertThrows(IllegalArgumentException.class, () -> context.withGuidance(context.guidance().complete())
                .guidance().validateActions(context, 12, List.of(payment)));
    }

    @Test void colloquialAmountsAreGroundedWithoutConfusingZeroOrAccountDigits() {
        for (var entry : java.util.Map.of("还得补交三千五", 3500, "请转一百五", 150,
                "请转一万二", 12000, "请转三千零五", 3005).entrySet()) {
            String text = entry.getKey() + "给模拟商户";
            var match = new VictimGuidance.Match("4", 0, text, entry.getValue(),
                    FundPurpose.INVESTMENT_PAYMENT, "模拟商户", "NONE", "", false, "");
            assertEquals(entry.getValue().intValue(), VictimGuidance.decide(7, match, rules,
                    List.of(), List.of(), new DialogueLine(7, SpeakerRole.SCAMMER, text, null, null), false).amountYuan());
        }
        String text = "还得补交三千五，账号尾号8891";
        for (int wrong : List.of(3005, 8891)) {
            var match = new VictimGuidance.Match("4", 0, text, wrong,
                    FundPurpose.INVESTMENT_PAYMENT, "", "NONE", "", false, "");
            assertThrows(IllegalArgumentException.class, () -> VictimGuidance.decide(7, match, rules,
                    List.of(), List.of(), new DialogueLine(7, SpeakerRole.SCAMMER, text, null, null), false));
        }
    }

    @Test void smallPaymentAtThresholdIsRequiredButStillRespectsLimits() {
        var d = new VictimGuidance(7, "4", "4:7", "请转100元", 100, FundPurpose.INVESTMENT_PAYMENT,
                "模拟商户", 1, false, false, false, "配合请求");
        var f = new FundsContext(ScenarioCode.FAKE_INVESTMENT, 250, 0, 0, 0, 0, List.of(),
                0, 0, 0, 0, 0, new ActionGate.TrustGate(70, 40, 70, 85, 200));
        var required = d.requireSmallPayment(f);
        assertTrue(required.required());
        assertThrows(IllegalArgumentException.class, () -> required.validateActions(f, 7, List.of()));
        assertFalse(d.complete().requireSmallPayment(f).required());
        assertFalse(d.requireSmallPayment(funds(null)).required());
        var overCap = new VictimGuidance(7, "4", "4:7", "请转300元", 300, FundPurpose.INVESTMENT_PAYMENT,
                "模拟商户", 1, false, false, false, "配合请求");
        assertFalse(overCap.requireSmallPayment(f).required());
    }

    private final List<SceneRuleSnapshot> rules = List.of(new SceneRuleSnapshot("4", "拒付后配合",
            "适用情况：连续拒绝同笔请求\n行为指导：接受并执行", 1, "VICTIM"));

    private DialogueLine scammer(int round) { return new DialogueLine(round, SpeakerRole.SCAMMER, "请转1000元给模拟商户", null, null); }
    private DialogueLine refusal(int round) { return new DialogueLine(round, SpeakerRole.VICTIM, "我暂时不转", null, null); }
    private VictimGuidance.Match match(int previous) {
        return new VictimGuidance.Match("4", previous, "请转1000元给模拟商户", 1000, FundPurpose.INVESTMENT_PAYMENT,
                "模拟商户", previous == 0 ? "NONE" : "REFUSED", previous == 0 ? "" : "我暂时不转", false, "");
    }
    private VictimGuidance decision() {
        var first = VictimGuidance.decide(7, match(0), rules, List.of(), List.of(), scammer(7), false);
        var second = VictimGuidance.decide(8, match(7), rules, List.of(first), List.of(refusal(7)), scammer(8), false);
        return VictimGuidance.decide(9, match(8), rules, List.of(first, second), List.of(refusal(8)), scammer(9), false);
    }
    private FundsContext funds(VictimGuidance guidance) {
        return new FundsContext(ScenarioCode.FAKE_INVESTMENT, 250, 3000, 0, 0, 0, List.of(),
                0, 1000, 2000, 0, 0, new ActionGate.TrustGate(20, 40, 70, 85, 1000), guidance);
    }
    private FundAction release(FundReserveTier tier, int amount) {
        return new FundAction(FundFlowType.RESERVE_RELEASE, FundPurpose.RESERVE_RELEASE, amount, 9,
                SpeakerRole.VICTIM, "已释放" + amount + "元", 1, "为本笔付款释放普通预留", null, tier);
    }
    private FundAction pay(int amount, int order) {
        return new FundAction(FundFlowType.PAYMENT, FundPurpose.INVESTMENT_PAYMENT, amount, 9,
                SpeakerRole.VICTIM, "已实际转账" + amount + "元", order, null, null, null);
    }
    @Test void thirdOccurrenceIsRoundNineAndOnlyTheThirdOccurrenceRequiresPayment() {
        var d = decision();
        assertEquals(9, d.roundNo()); assertEquals(3, d.consecutive()); assertEquals("4:7", d.requestId());
        assertTrue(d.required()); assertTrue(d.paymentAllowed(funds(d)));
        assertEquals(20, funds(d).gate().score(), "a request permission must never raise trust");
    }
    @Test void interruptionsAndChangedRequestsDoNotInheritCounts() {
        var first = VictimGuidance.decide(7, match(0), rules, List.of(), List.of(), scammer(7), false);
        var afterGap = VictimGuidance.decide(9, match(7), rules, List.of(first), List.of(refusal(7)), scammer(9), false);
        assertEquals(1, afterGap.consecutive()); assertFalse(afterGap.required());
        var changed = new VictimGuidance.Match("4", 7, "请转1000元给模拟商户", 999,
                FundPurpose.INVESTMENT_PAYMENT, "模拟商户", "REFUSED", "我暂时不转", false, "");
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.decide(8, changed, rules, List.of(first),
                List.of(refusal(7)), scammer(8), false));
    }
    @Test void unknownRulesAndFabricatedQuotesCannotGrantPermission() {
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.decide(3, match(0), List.of(), List.of(), List.of(), scammer(3), false));
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.decide(3, match(0), rules, List.of(), List.of(),
                new DialogueLine(3, SpeakerRole.SCAMMER, "今天不需要付款", null, null), false));
    }
    @Test void changedRequestStartsAgainWithoutInheritingPermissionOrOldResponseEvidence() {
        var first = VictimGuidance.decide(7, match(0), rules, List.of(), List.of(), scammer(7), false);
        var current = new DialogueLine(8, SpeakerRole.SCAMMER, "请转2000元给模拟商户", null, null);
        var changed = new VictimGuidance.Match("4", 7, current.content(), 2000,
                FundPurpose.INVESTMENT_PAYMENT, "模拟商户", "REFUSED", "我暂时不转", false, "");
        var automatic = VictimGuidance.decide(8, changed, rules, List.of(first), List.of(), current, false);
        assertEquals(1, automatic.consecutive()); assertFalse(automatic.required());
        assertEquals(2000, automatic.amountYuan()); assertEquals("4:8", automatic.requestId());
        var corrected = new VictimGuidance.Match("4", 0, current.content(), 2000,
                FundPurpose.INVESTMENT_PAYMENT, "模拟商户", "NONE", "", false, "");
        var decision = VictimGuidance.decide(8, corrected, rules, List.of(first), List.of(refusal(7)), current, false);
        assertEquals(1, decision.consecutive()); assertFalse(decision.required());
        assertEquals(2000, decision.amountYuan()); assertEquals("4:8", decision.requestId());
    }
    @Test void increasedLoanRequestInRoundTwoStartsAtOneAndOnlyItsThirdOccurrenceCanGrantPermission() {
        var loanRules = List.of(new SceneRuleSnapshot("5", "重复等待确认后配合借款", "第三次接受本笔借款", 1, "VICTIM"));
        var first = new VictimGuidance(1, "5", "5:1", "先帮我转2000块应急", 2000,
                FundPurpose.LOAN_TRANSFER, "", 1, false, false, false, "第三次接受本笔借款");
        var saved = new java.util.ArrayList<>(List.of(first));
        var history = new java.util.ArrayList<DialogueLine>();
        for (int round = 2; round <= 4; round++) {
            var current = new DialogueLine(round, SpeakerRole.SCAMMER, "医院催缴3000块押金，请转给模拟医院", null, null);
            var match = new VictimGuidance.Match("5", round - 1, current.content(), 3000, FundPurpose.LOAN_TRANSFER,
                    "模拟医院", "REFUSED", "先发缴费单", false, "");
            var decision = VictimGuidance.decide(round, match, loanRules, saved, history, current, false);
            assertEquals(round - 1, decision.consecutive()); assertEquals("5:2", decision.requestId());
            assertEquals(round == 4, decision.required()); assertEquals(3000, decision.amountYuan());
            saved.add(decision); history.add(current);
            history.add(new DialogueLine(round, SpeakerRole.VICTIM, "先发缴费单", null, null));
        }
    }
    @Test void dialogueWithoutSavedRequestsAlwaysStartsAtOneDespiteAModelHistoryReference() {
        var historical = new VictimGuidance.Match("4", 7, scammer(8).content(), 1000,
                FundPurpose.INVESTMENT_PAYMENT, "模拟商户", "REFUSED", refusal(7).content(), false, "");
        var decision = VictimGuidance.decide(8, historical, rules, List.of(),
                List.of(scammer(7), refusal(7)), scammer(8), false);
        assertEquals(1, decision.consecutive()); assertEquals("4:8", decision.requestId());
        assertFalse(decision.required()); assertFalse(decision.paymentAllowed(funds(decision)));
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.decide(8, historical,
                rules, List.of(), List.of(scammer(7), refusal(7)),
                new DialogueLine(8, SpeakerRole.SCAMMER, "今天不需要付款", null, null), false));
        var blocked = VictimGuidance.decide(8, historical, rules, List.of(), List.of(refusal(7)), scammer(8), true);
        assertTrue(blocked.blocked()); assertFalse(blocked.required());
    }
    @Test void unknownReferencesWithSavedRequestsStillRequireCorrection() {
        var first = VictimGuidance.decide(6, match(0), rules, List.of(), List.of(), scammer(6), false);
        var error = assertThrows(IllegalArgumentException.class, () -> VictimGuidance.decide(8, match(7),
                rules, List.of(first), List.of(scammer(7), refusal(7)), scammer(8), false));
        assertTrue(error.getMessage().contains("可引用的已保存请求轮号=[6]"));
        assertTrue(error.getMessage().contains("不能使用仅出现在正式对话中的轮号"));
    }
    @Test void amountsAndRecipientsMustBeSupportedByTheContactsMessages() {
        for (String text : List.of("请转两千元给模拟商户", "请转2,000元给模拟商户", "请转2000给模拟商户")) {
            var match = new VictimGuidance.Match("4", 0, text, 2000, FundPurpose.INVESTMENT_PAYMENT,
                    "模拟商户", "NONE", "", false, "");
            assertEquals(2000, VictimGuidance.decide(7, match, rules, List.of(), List.of(),
                    new DialogueLine(7, SpeakerRole.SCAMMER, text, null, null), false).amountYuan());
        }
        for (String text : List.of("请先付费用", "请转1000.5元给模拟商户")) {
            var match = new VictimGuidance.Match("4", 0, text, 1000, FundPurpose.INVESTMENT_PAYMENT,
                    "模拟商户", "NONE", "", false, "");
            assertThrows(IllegalArgumentException.class, () -> VictimGuidance.decide(7, match, rules, List.of(), List.of(),
                    new DialogueLine(7, SpeakerRole.SCAMMER, text, null, null), false));
        }
        String text = "请转1000元给另一商户";
        var mismatch = new VictimGuidance.Match("4", 0, text, 1000, FundPurpose.INVESTMENT_PAYMENT,
                "模拟商户", "NONE", "", false, "");
        var unknown = VictimGuidance.decide(7, mismatch, rules, List.of(), List.of(),
                new DialogueLine(7, SpeakerRole.SCAMMER, text, null, null), false);
        assertEquals("", unknown.recipient());
        assertFalse(unknown.paymentAllowed(funds(unknown)));
    }
    @Test void supplyingAMissingRecipientKeepsTheDecisionButRequiresContactEvidence() {
        var previous = new VictimGuidance(12, "4", "4:10", "充值1000元", 1000,
                FundPurpose.INVESTMENT_PAYMENT, "", 3, true, false, false, "重复拒绝后配合");
        var response = new DialogueLine(12, SpeakerRole.VICTIM, "好，这笔转到哪个模拟账户？", null, null);
        var clarified = new VictimGuidance.Match("4", 12, scammer(13).content(), 1000,
                FundPurpose.INVESTMENT_PAYMENT, "模拟商户", "ACCEPTED", response.content(), false, "");
        var decision = VictimGuidance.decide(13, clarified, rules, List.of(previous), List.of(response), scammer(13), false);
        assertEquals("4:10", decision.requestId()); assertEquals(3, decision.consecutive());
        assertTrue(decision.paymentAllowed(funds(decision)));
        var ungrounded = new VictimGuidance.Match("4", 12, "请转1000元", 1000,
                FundPurpose.INVESTMENT_PAYMENT, "模拟商户", "ACCEPTED", response.content(), false, "");
        var unknown = VictimGuidance.decide(13, ungrounded, rules,
                List.of(previous), List.of(response), new DialogueLine(13, SpeakerRole.SCAMMER, "请转1000元", null, null), false);
        assertEquals("", unknown.recipient());
        assertEquals(previous.requestId(), unknown.requestId());
        assertFalse(unknown.paymentAllowed(funds(unknown)));
        assertTrue(unknown.awaitingRecipient(funds(unknown)));
        assertThrows(IllegalArgumentException.class, () -> unknown.validateActions(funds(unknown), 13,
                List.of(new FundAction(FundFlowType.PAYMENT, FundPurpose.INVESTMENT_PAYMENT,
                        1000, 13, SpeakerRole.VICTIM, "已实际转账1000元", 1, null, null))));
        var redirected = new VictimGuidance.Match("4", 13, "请转1000元给另一商户", 1000,
                FundPurpose.INVESTMENT_PAYMENT, "另一商户", "NONE", "", false, "");
        var newRecipient = VictimGuidance.decide(14, redirected, rules,
                List.of(decision), List.of(), new DialogueLine(14, SpeakerRole.SCAMMER, redirected.requestQuote(), null, null), false);
        assertEquals(1, newRecipient.consecutive()); assertFalse(newRecipient.required());
    }
    @Test void inventedRecipientIsDiscardedWithoutInventingAnInvestmentAccountOrBlockingDialogue() {
        for (var purpose : List.of(FundPurpose.INVESTMENT_PAYMENT, FundPurpose.EXTRA_FEE_PAYMENT,
                FundPurpose.INITIAL_PAYMENT, FundPurpose.LOAN_TRANSFER)) {
            var current = new DialogueLine(15, SpeakerRole.SCAMMER, "本笔请支付1000元", null, null);
            var match = new VictimGuidance.Match("4", 0, current.content(), 1000, purpose,
                    "模型编造的平台账户", "NONE", "", false, "");
            var decision = VictimGuidance.decide(15, match, rules, List.of(),
                    List.of(new DialogueLine(14, SpeakerRole.VICTIM, "模型编造的平台账户", null, null)), current, false);
            assertEquals("", decision.recipient());
            assertEquals(1000, decision.amountYuan());
            assertEquals(purpose, decision.purpose());
            assertFalse(decision.paymentAllowed(funds(decision)));
        }
    }
    @Test void aVerbalPaymentPromiseIsNotACompletedPaymentAndStillCountsAsUnexecuted() {
        var first = VictimGuidance.decide(7, match(0), rules, List.of(), List.of(), scammer(7), false);
        var promise = new VictimGuidance.Match("4", 7, "请转1000元给模拟商户", 1000,
                FundPurpose.INVESTMENT_PAYMENT, "模拟商户", "ACCEPTED", "我准备转", false, "");
        var second = VictimGuidance.decide(8, promise, rules, List.of(first),
                List.of(new DialogueLine(7, SpeakerRole.VICTIM, "我准备转", null, null)), scammer(8), false);
        assertFalse(second.fulfilled()); assertEquals(2, second.consecutive());
        var third = VictimGuidance.decide(9, match(8), rules, List.of(first, second), List.of(refusal(8)), scammer(9), false);
        assertTrue(third.required());
        var canonical = VictimGuidance.decide(9, match(7), rules,
                List.of(first, second), List.of(refusal(7)), scammer(9), false);
        assertTrue(canonical.required()); assertEquals(3, canonical.consecutive());
        assertEquals(second.requestId(), canonical.requestId());
        var completed = second.complete();
        var repeated = VictimGuidance.decide(9, match(7), rules,
                List.of(first, completed), List.of(refusal(7)), scammer(9), false);
        assertTrue(repeated.fulfilled()); assertFalse(repeated.required());
    }
    @Test void exactPaymentCanUseOrdinaryReserveWithoutChangingTrust() {
        var f = funds(decision());
        var result = FundRules.apply(f.summary(), f, 9, 1, List.of(release(FundReserveTier.GENERAL, 750), pay(1000, 2)));
        assertEquals(1000, result.after().totalPaidYuan()); assertEquals(0, result.after().availableBalanceYuan());
        assertEquals(750, result.after().generalReserveReleasedYuan()); assertEquals(0, result.after().emergencyReserveReleasedYuan());
        var ordinary = funds(null);
        assertThrows(IllegalArgumentException.class, () -> FundRules.apply(ordinary.summary(), ordinary, 9, 1,
                List.of(release(FundReserveTier.GENERAL, 750), pay(1000, 2))));
    }
    @Test void noOpExtraPaymentsEmergencyReserveAndStaleRoundAreRejected() {
        var f = funds(decision());
        assertThrows(IllegalArgumentException.class, () -> FundRules.apply(f.summary(), f, 9, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> FundRules.apply(f.summary(), f, 9, 1,
                List.of(release(FundReserveTier.EMERGENCY, 750), pay(1000, 2))));
        assertThrows(IllegalArgumentException.class, () -> FundRules.apply(f.summary(), f, 9, 1,
                List.of(pay(200, 1), pay(800, 2))));
        assertThrows(IllegalArgumentException.class, () -> FundRules.apply(f.summary(), f, 10, 1,
                List.of(release(FundReserveTier.GENERAL, 750), pay(1000, 2))));
        assertThrows(RuntimeException.class, () -> AgentOutputValidator.funds(SpeakerRole.VICTIM,
                "好，我考虑一下", null, null, List.of(), 9, f, List.of(), true));
    }
    @Test void completedPaymentsAndKnownCounterevidenceStayBlocked() {
        var completed = decision().complete();
        completed.validateBehavior(funds(completed), "没有新动作", List.of());
        assertThrows(IllegalArgumentException.class, () -> completed.validateBehavior(funds(completed), "已转账1000元", List.of(pay(1000, 1))));
        var blocked = VictimGuidance.decide(9, match(0), rules, List.of(), List.of(), scammer(9), true);
        assertFalse(blocked.required());
        assertThrows(IllegalArgumentException.class, () -> blocked.validateBehavior(funds(blocked), "已转账1000元", List.of(pay(1000, 1))));
    }
    @Test void insufficientOrdinaryFundsCannotUseEmergencyForThisPermission() {
        var d = new VictimGuidance(9, "4", "4:7", "请转2000元", 2000, FundPurpose.INVESTMENT_PAYMENT,
                "模拟商户", 3, true, false, false, "配合当前请求");
        assertFalse(d.paymentAllowed(funds(d)));
        var highTrust = new FundsContext(ScenarioCode.FAKE_INVESTMENT, 250, 3000, 0, 0, 0, List.of(),
                0, 1000, 2000, 0, 0, new ActionGate.TrustGate(99, 40, 70, 85, 200), d);
        assertThrows(IllegalArgumentException.class, () -> FundRules.apply(highTrust.summary(), highTrust, 9, 1,
                List.of(release(FundReserveTier.GENERAL, 1000), release(FundReserveTier.EMERGENCY, 750), pay(2000, 3))));
        assertThrows(IllegalArgumentException.class, () -> FundRules.apply(highTrust.summary(), highTrust, 9, 1, List.of(pay(100, 1))));
        assertDoesNotThrow(() -> FundRules.apply(highTrust.summary(), highTrust, 9, 1, List.of()));
    }
    @Test void acceptedExplanationsPersistAcrossLaterRoundsButDoNotPretendVerification() {
        var accepted = new VictimGuidance(3, "4", "4:1", "请继续", 0, null, "", 3, true, true, false, "接受解释继续");
        var match = new VictimGuidance.Match("4", 3, "请继续", 0, null, "", "NONE", "", false, "");
        var later = VictimGuidance.decide(6, match, rules, List.of(accepted), List.of(),
                new DialogueLine(6, SpeakerRole.SCAMMER, "请继续", null, null), false);
        assertTrue(later.required()); assertTrue(later.fulfilled());
        assertThrows(IllegalArgumentException.class, () -> later.validateBehavior(funds(later), "准备等核实后继续", List.of()));
        later.validateBehavior(funds(later), "已接受解释并继续了解安排", List.of());
        assertTrue(later.prompt(funds(later)).contains("不得虚构独立核实完成"));
    }
    @Test void anUnknownPaymentAmountIsNotTreatedAsANonFinancialInstruction() {
        var unknown = new VictimGuidance(9, "4", "4:7", "先付这笔费用", 0, FundPurpose.INVESTMENT_PAYMENT,
                "模拟商户", 3, true, false, false, "接受并付款");
        assertFalse(unknown.paymentAllowed(funds(unknown)));
        unknown.validateBehavior(funds(unknown), null, List.of());
        assertThrows(IllegalArgumentException.class, () -> unknown.validateBehavior(funds(unknown), "已转账1000元", List.of(pay(1000, 1))));
        assertTrue(unknown.prompt(funds(unknown)).contains("金额不明"));
    }
}

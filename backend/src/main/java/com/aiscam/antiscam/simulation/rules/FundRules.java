package com.aiscam.antiscam.simulation.rules;

import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import java.util.*;

public final class FundRules {
    private FundRules() {}
    public static int actualLoss(int paid, int refunded) { return actualLoss(paid, refunded, 0); }
    public static int actualLoss(int paid, int refunded, int rewarded) { return (int) Math.max(0L, (long) paid - refunded - rewarded); }

    public static FundApplication apply(FundsSummary before, ScenarioCode scenario, int reservedTotal, int roundNo,
            int nextSequenceNo, List<FundAction> actions, List<FundFlow> existing) {
        return apply(before, new FundsContext(scenario, before.availableBalanceYuan(), reservedTotal,
                before.reserveReleasedYuan(), before.totalPaidYuan(), before.totalRefundedYuan(), existing),
                roundNo, nextSequenceNo, actions);
    }

    public static FundApplication apply(FundsSummary before, FundsContext context, int roundNo,
            int nextSequenceNo, List<FundAction> actions) {
        var scenario = context.scenarioCode();
        var existing = context.existingFlows();
        long balance = before.availableBalanceYuan(), paid = before.totalPaidYuan(), refunded = before.totalRefundedYuan();
        long rewarded = before.totalRewardedYuan();
        long general = before.generalReserveReleasedYuan(), emergency = before.emergencyReserveReleasedYuan();
        long paymentTotal = actions.stream().filter(a -> a != null && a.type() == FundFlowType.PAYMENT).mapToLong(FundAction::amountYuan).sum();
        long gap = Math.max(0, paymentTotal - balance), batchReleased = 0;
        int additional = before.additionalPaymentCount();
        var postings = new ArrayList<FundPosting>();
        var refundByPayment = new HashMap<Long, Long>();
        for (var f : existing) if (f.getFlowType() == FundFlowType.REFUND) refundByPayment.merge(f.getRelatedPaymentId(), (long) f.getAmountYuan(), Long::sum);
        int previousOrder = 0;
        var seen = new HashSet<String>();
        for (var a : actions) {
            require(a != null && a.type() != null && a.purpose() != null && a.amountYuan() > 0, "invalid fund amount/type");
            require(a.roundNo() == roundNo && a.eventOrder() > previousOrder, "fund action order/round mismatch");
            previousOrder = a.eventOrder();
            require(seen.add(a.type() + "|" + a.purpose() + "|" + a.amountYuan() + "|" + a.quote()), "duplicate fund action");
            require(a.reason() == null || a.reason().length() <= 255, "fund reason too long");
            boolean payout = a.type() == FundFlowType.REFUND || a.type() == FundFlowType.REWARD;
            require(a.speaker() == (payout ? SpeakerRole.SCAMMER : SpeakerRole.VICTIM), "wrong fund action speaker");
            if (a.type() != FundFlowType.REFUND) require(a.relatedPaymentId() == null, "unexpected related payment");
            if (a.type() != FundFlowType.RESERVE_RELEASE) require(a.reserveTier() == null, "unexpected reserve tier");
            if (context.v3() && !payout) require(ActionGate.decide(context.gate(), a, (int) (paid - refunded), (int) balance,
                    (int) (context.generalReserveTotalYuan() - general), (int) (context.emergencyReserveTotalYuan() - emergency))
                    == ActionGate.GateDecision.ALLOW, "fund action is not permitted");
            switch (a.type()) {
                case PAYMENT -> {
                    require(switch (a.purpose()) {
                        case INITIAL_PAYMENT, ADDITIONAL_PAYMENT -> !context.v3() || scenario == ScenarioCode.TASK_REBATE;
                        case INVESTMENT_PAYMENT, EXTRA_FEE_PAYMENT -> !context.v3() || scenario == ScenarioCode.FAKE_INVESTMENT;
                        case LOAN_TRANSFER -> !context.v3() || scenario == ScenarioCode.IMPERSONATION_LOAN;
                        case OTHER_PAYMENT -> true;
                        default -> false;
                    }, "invalid payment purpose");
                    require(a.amountYuan() <= balance, "payment exceeds balance");
                    if (paid > 0) additional++;
                    balance -= a.amountYuan(); paid += a.amountYuan();
                }
                case REFUND -> {
                    require(a.speaker() == SpeakerRole.SCAMMER && a.purpose() == FundPurpose.REFUND, "scammer refund required");
                    require(scenario != ScenarioCode.IMPERSONATION_LOAN, "loan scenario cannot refund");
                    var payment = existing.stream().filter(f -> Objects.equals(f.getId(), a.relatedPaymentId())
                            && f.getFlowType() == FundFlowType.PAYMENT).findFirst().orElse(null);
                    require(a.relatedPaymentId() != null && payment != null, "refund payment not found in session");
                    long perPayment = refundByPayment.getOrDefault(a.relatedPaymentId(), 0L) + a.amountYuan();
                    require(perPayment <= payment.getAmountYuan(), "refund exceeds original payment");
                    refunded += a.amountYuan();
                    require(refunded + rewarded <= 300 && refunded <= paid, "refund exceeds session cap");
                    refundByPayment.put(a.relatedPaymentId(), perPayment);
                    balance += a.amountYuan();
                }
                case REWARD -> {
                    require(context.v3() && scenario != ScenarioCode.IMPERSONATION_LOAN && a.purpose() == FundPurpose.REWARD,
                            "reward is not allowed");
                    rewarded += a.amountYuan();
                    require(refunded + rewarded <= 300, "payout exceeds session cap");
                    balance += a.amountYuan();
                }
                case RESERVE_RELEASE -> {
                    require(a.purpose() == FundPurpose.RESERVE_RELEASE && a.reason() != null && !a.reason().isBlank(), "reserve release needs reason");
                    if (context.v3()) {
                        batchReleased += a.amountYuan();
                        require(batchReleased <= gap, "reserve release exceeds payment gap");
                        require(a.reserveTier() != null, "reserve tier required");
                        if (a.reserveTier() == FundReserveTier.GENERAL) {
                            general += a.amountYuan();
                            require(general <= context.generalReserveTotalYuan(), "general reserve exceeded");
                        } else {
                            require(general == context.generalReserveTotalYuan(), "general reserve must be used first");
                            emergency += a.amountYuan();
                            require(emergency <= context.emergencyReserveTotalYuan(), "emergency reserve exceeded");
                        }
                    } else {
                        require(a.reserveTier() == null, "legacy session cannot use reserve tiers");
                        general += a.amountYuan();
                        require(general + emergency <= context.reservedFundsTotalYuan(), "reserve release exceeds frozen reserve");
                    }
                    balance += a.amountYuan();
                }
            }
            require(balance <= Integer.MAX_VALUE && paid <= Integer.MAX_VALUE && refunded <= Integer.MAX_VALUE
                    && rewarded <= Integer.MAX_VALUE && general + emergency <= Integer.MAX_VALUE && additional <= 65535, "fund summary overflow");
            postings.add(new FundPosting(a.type(), a.purpose(), a.amountYuan(), nextSequenceNo++, (int) balance, a.reason(), a.relatedPaymentId(), a.reserveTier()));
        }
        return new FundApplication(List.copyOf(postings), new FundsSummary(before.initialAvailableYuan(), (int) balance,
                (int) paid, (int) refunded, (int) rewarded, actualLoss((int) paid, (int) refunded, (int) rewarded),
                additional, (int) general, (int) emergency));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}

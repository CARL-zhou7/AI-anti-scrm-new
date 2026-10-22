package com.aiscam.antiscam.simulation.rules;

import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import java.util.*;

public final class FundRules {
    private FundRules() {}
    public static int actualLoss(int paid, int refunded) { return Math.max(0, paid - refunded); }

    public static FundApplication apply(FundsSummary before, ScenarioCode scenario, int reservedTotal, int roundNo,
            int nextSequenceNo, List<FundAction> actions, List<FundFlow> existing) {
        long balance = before.availableBalanceYuan(), paid = before.totalPaidYuan(), refunded = before.totalRefundedYuan();
        long released = before.reserveReleasedYuan();
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
            if (a.type() != FundFlowType.REFUND) require(a.relatedPaymentId() == null && a.speaker() == SpeakerRole.VICTIM, "victim action required");
            switch (a.type()) {
                case PAYMENT -> {
                    require(a.purpose() != FundPurpose.REFUND && a.purpose() != FundPurpose.RESERVE_RELEASE, "invalid payment purpose");
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
                    require(refunded <= 300 && refunded <= paid, "refund exceeds session cap");
                    refundByPayment.put(a.relatedPaymentId(), perPayment);
                    balance += a.amountYuan();
                }
                case RESERVE_RELEASE -> {
                    require(a.purpose() == FundPurpose.RESERVE_RELEASE && a.reason() != null && !a.reason().isBlank(), "reserve release needs reason");
                    released += a.amountYuan();
                    require(released <= reservedTotal, "reserve release exceeds frozen reserve");
                    balance += a.amountYuan();
                }
            }
            require(balance <= Integer.MAX_VALUE && paid <= Integer.MAX_VALUE && refunded <= Integer.MAX_VALUE
                    && additional <= 65535, "fund summary overflow");
            postings.add(new FundPosting(a.type(), a.purpose(), a.amountYuan(), nextSequenceNo++, (int) balance, a.reason(), a.relatedPaymentId()));
        }
        return new FundApplication(List.copyOf(postings), new FundsSummary(before.initialAvailableYuan(), (int) balance,
                (int) paid, (int) refunded, actualLoss((int) paid, (int) refunded), additional, (int) released));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
